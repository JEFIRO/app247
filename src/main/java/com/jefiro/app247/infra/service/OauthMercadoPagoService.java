package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.MercadoPagoConta;
import com.jefiro.app247.domain.model.auth.RoleUser;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.dto.MercadoPagoTokenResponse;
import com.jefiro.app247.infra.repository.OauthMercadoPagoRepository;
import com.jefiro.app247.infra.exception.ExternalServiceException;
import com.jefiro.app247.infra.exception.ApiBusinessException;
import com.jefiro.app247.infra.dto.mercadopago.MercadoPagoOauthResultResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class OauthMercadoPagoService {
    private static final String STATE_PREFIX = "oauth:mp:";
    private static final String RESULT_PREFIX = "oauth:mp:result:";

    @Value("${spring.mp.id}")
    private String clientId;
    @Value("${spring.mp.redirect_url.oauth}")
    private String redirectUri;
    @Value("${spring.mp.id.secret}")
    private String clientSecret;

    private final RedisTemplate<String, String> redisTemplate;
    private final UserService userService;
    private final OauthMercadoPagoRepository repository;
    private final RestTemplate restTemplate;
    @org.springframework.beans.factory.annotation.Autowired
    private MercadoPagoAccountLifecycleService accountLifecycleService;

    public OauthMercadoPagoService(RedisTemplate<String, String> redisTemplate,
                                   UserService userService,
                                   OauthMercadoPagoRepository repository,
                                   RestTemplate restTemplate) {
        this.redisTemplate = redisTemplate;
        this.userService = userService;
        this.repository = repository;
        this.restTemplate = restTemplate;
    }

    public MercadoPagoConta getByEmpresa(String empresaId) {
        return accountLifecycleService.getAtivaPorEmpresa(empresaId);
    }

    public MercadoPagoConta getByMpUserId(String mpUserId) {
        return accountLifecycleService.getAtivaPorMpUserId(mpUserId);
    }

    public void desvincularContaAtual() {
        accountLifecycleService.desvincular(EmpresaContext.require());
    }

    public String url(User gestor) {
        return url(gestor, false);
    }

    public String url(User gestor, boolean substituirConta) {
        validarGestorDoContexto(gestor);
        redisTemplate.delete(RESULT_PREFIX + gestor.getEmpresa().getId());
        String state = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(
                STATE_PREFIX + state,
                gestor.getIdUser() + "|" + gestor.getEmpresa().getId() + "|" + substituirConta,
                Duration.ofMinutes(10)
        );

        return "https://auth.mercadopago.com.br/authorization"
                + "?client_id=" + clientId
                + "&response_type=code"
                + "&platform_id=mp"
                + "&state=" + state
                + "&redirect_uri=" + redirectUri;
    }

    public void gerarToken(String code, String state) {
        String key = STATE_PREFIX + state;
        String stateValue = redisTemplate.opsForValue().get(key);
        if (stateValue == null) {
            throw new IllegalStateException("State inválido ou expirado");
        }

        String[] partes = stateValue.split("\\|", 3);
        if (partes.length < 2) {
            throw new IllegalStateException("State OAuth inválido");
        }
        String userId = partes[0];
        String empresaId = partes[1];
        boolean substituirConta = partes.length == 3 && Boolean.parseBoolean(partes[2]);
        User gestor = userService.getUser(userId);
        if (gestor.getEmpresa() == null || !empresaId.equals(gestor.getEmpresa().getId()) || !isGestor(gestor)) {
            throw new IllegalStateException("Gestor não pode autorizar Mercado Pago para esta empresa");
        }

        try {
            MercadoPagoTokenResponse token = trocarCodigo(code);
            try {
                accountLifecycleService.autorizar(empresaId, token, substituirConta);
            } catch (DataIntegrityViolationException conflict) {
                accountLifecycleService.reautorizarAposConflito(empresaId, token);
            }
        } catch (ApiBusinessException business) {
            if ("MERCADO_PAGO_ACCOUNT_ALREADY_LINKED".equals(business.getCode())) {
                accountLifecycleService.registrarConflito(empresaId);
            }
            registrarResultado(empresaId, business.getCode());
            throw business;
        } catch (RuntimeException failure) {
            registrarResultado(empresaId, "MERCADO_PAGO_OAUTH_FAILED");
            throw failure;
        } finally {
            redisTemplate.delete(key);
        }
    }

    public MercadoPagoOauthResultResponse consultarResultado() {
        String empresaId = EmpresaContext.require();
        String key = RESULT_PREFIX + empresaId;
        String code = redisTemplate.opsForValue().get(key);
        if (code == null) {
            return null;
        }
        return new MercadoPagoOauthResultResponse(code, mensagemSegura(code));
    }

    private void registrarResultado(String empresaId, String code) {
        redisTemplate.opsForValue().set(
                RESULT_PREFIX + empresaId,
                code,
                Duration.ofMinutes(10)
        );
    }

    private String mensagemSegura(String code) {
        return switch (code) {
            case "MERCADO_PAGO_ACCOUNT_ALREADY_LINKED" ->
                    "Esta conta Mercado Pago já está vinculada a outra empresa no sistema.";
            case "MERCADO_PAGO_ACCOUNT_REPLACEMENT_REQUIRED" ->
                    "Esta Empresa já possui outra conta Mercado Pago. Confirme a substituição para continuar.";
            default -> "Não foi possível concluir a autorização do Mercado Pago.";
        };
    }

    private MercadoPagoTokenResponse trocarCodigo(String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("grant_type", "authorization_code");
        body.add("code", code);
        body.add("redirect_uri", redirectUri);

        ResponseEntity<MercadoPagoTokenResponse> response;
        try {
            response = restTemplate.postForEntity(
                    "https://api.mercadopago.com/oauth/token",
                    new HttpEntity<>(body, headers),
                    MercadoPagoTokenResponse.class
            );
        } catch (RestClientException e) {
            throw new ExternalServiceException("Mercado Pago",
                    "Falha ao trocar código OAuth", e);
        }
        if (response.getBody() == null) {
            throw new IllegalStateException("Mercado Pago retornou OAuth sem corpo");
        }
        return response.getBody();
    }

    private void validarGestorDoContexto(User gestor) {
        if (gestor == null || gestor.getEmpresa() == null
                || !EmpresaContext.require().equals(gestor.getEmpresa().getId()) || !isGestor(gestor)) {
            throw new IllegalStateException("Gestor não pode autorizar Mercado Pago para esta empresa");
        }
    }

    private boolean isGestor(User user) {
        return user.getRole() == RoleUser.ADMIN || user.getRole() == RoleUser.GERENTE;
    }
}
