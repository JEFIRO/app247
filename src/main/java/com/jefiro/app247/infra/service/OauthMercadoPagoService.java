package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.MercadoPagoConta;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.dto.MercadoPagoTokenResponse;
import com.jefiro.app247.domain.model.dto.OrderResponse;
import com.jefiro.app247.domain.model.dto.mercadopago.*;
import com.jefiro.app247.infra.repository.OauthMercadoPagoRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;


@Service
public class OauthMercadoPagoService {
    @Value("${spring.mp.id}")
    private String CLIENT_ID;
    @Value("${spring.mp.redirect_url.oauth}")
    private String REDIRECT_URI;
    @Value("${spring.mp.id.secret}")
    private String CLIENT_SECRET;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    @Autowired
    UserService userService;
    @Autowired
    OauthMercadoPagoRepository repository;


    public MercadoPagoConta getByEmpresa(String id) {
        return repository.findByEmpresa_Id(id).orElseThrow();
    }


    private String iniciarAutorizacao(String user) {
        String state = UUID.randomUUID().toString();

        redisTemplate.opsForValue().set(
                "oauth:mp:" + state,
                user,
                Duration.ofMinutes(10)
        );
        return state;

    }

    public String url(String usuarioId) {


        String state = iniciarAutorizacao(usuarioId);

        return "https://auth.mercadopago.com.br/authorization" +
                "?client_id=" + CLIENT_ID +
                "&response_type=code" +
                "&platform_id=mp" +
                "&state=" + state +
                "&redirect_uri=" + REDIRECT_URI;
    }

    @Transactional
    public void gerarToken(String code, String state) {

        String key = "oauth:mp:" + state;

        String userId = redisTemplate.opsForValue().get(key);
        User user = userService.getUser(userId);
        if (userId == null) {
            throw new IllegalStateException("State inválido ou expirado");
        }

        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", CLIENT_ID);
        body.add("client_secret", CLIENT_SECRET);
        body.add("grant_type", "authorization_code");
        body.add("code", code);
        body.add("redirect_uri", REDIRECT_URI);

        HttpEntity<MultiValueMap<String, String>> request =
                new HttpEntity<>(body, headers);

        ResponseEntity<MercadoPagoTokenResponse> response =
                restTemplate.postForEntity(
                        "https://api.mercadopago.com/oauth/token",
                        request,
                        MercadoPagoTokenResponse.class
                );


        MercadoPagoConta mercadoPagoConta = new MercadoPagoConta(response.getBody());
        mercadoPagoConta.setMpUserId(userId);
        mercadoPagoConta.setEmpresa(user.getEmpresa());
        repository.save(mercadoPagoConta);
    }

    public List<TerminalResponse> listarTerminais(String idUser) {
        MercadoPagoConta mercadoPagoConta = repository.findByMpUserId(idUser).orElseThrow();
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(mercadoPagoConta.getAccessToken());

        HttpEntity<Void> entity =
                new HttpEntity<>(headers);

        ResponseEntity<ListaTerminaisResponse> response =
                restTemplate.exchange("https://api.mercadopago.com/terminals/v1/list",
                        HttpMethod.GET, entity, ListaTerminaisResponse.class);


        System.out.println(response);

        List<TerminalResponse> terminais =
                response.getBody().data().terminals();
        return terminais;
    }

    public Boolean setTerminal(String idUser, TerminalResponse response) {
        try {

            MercadoPagoConta mercadoPagoConta = repository.findByMpUserId(idUser).orElseThrow();
            mercadoPagoConta.setTerminalId(response.id());
            repository.save(mercadoPagoConta);
            return true;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
