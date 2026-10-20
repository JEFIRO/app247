package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.MercadoPagoConta;
import com.jefiro.app247.domain.model.auth.RoleUser;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.dto.MercadoPagoTokenResponse;
import com.jefiro.app247.infra.repository.OauthMercadoPagoRepository;
import com.jefiro.app247.infra.exception.ApiBusinessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.http.HttpEntity;
import org.springframework.util.MultiValueMap;
import org.mockito.ArgumentCaptor;
import com.jefiro.app247.infra.exception.ExternalServiceException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OauthMercadoPagoServiceTest {
    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock ValueOperations<String, String> valueOperations;
    @Mock UserService userService;
    @Mock OauthMercadoPagoRepository repository;
    @Mock RestTemplate restTemplate;
    @Mock MercadoPagoAccountLifecycleService accountLifecycleService;

    @AfterEach
    void limparContexto() {
        EmpresaContext.clear();
    }

    @Test
    void novoOauthAtualizaContaExistenteDaEmpresa() {
        Empresa empresa = Empresa.builder().id("empresa-a").build();
        User gestor = User.builder().idUser("gestor-a").empresa(empresa).role(RoleUser.ADMIN).build();
        MercadoPagoTokenResponse token = new MercadoPagoTokenResponse(
                "novo", "bearer", 21600L, "offline_access", "mp-user-a",
                "refresh-novo", "public", true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("oauth:mp:state-a")).thenReturn("gestor-a|empresa-a");
        when(userService.getUser("gestor-a")).thenReturn(gestor);
        when(restTemplate.postForEntity(anyString(), any(), eq(MercadoPagoTokenResponse.class)))
                .thenReturn(ResponseEntity.ok(token));

        service().gerarToken("code", "state-a");

        verify(accountLifecycleService).autorizar("empresa-a", token, false);
        verify(redisTemplate).delete("oauth:mp:state-a");
    }

    @Test
    void rejeitaCallbackSeGestorMudouDeEmpresa() {
        Empresa empresaB = Empresa.builder().id("empresa-b").build();
        User gestor = User.builder().idUser("gestor-a").empresa(empresaB).role(RoleUser.ADMIN).build();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("oauth:mp:state-a")).thenReturn("gestor-a|empresa-a");
        when(userService.getUser("gestor-a")).thenReturn(gestor);

        assertThrows(IllegalStateException.class, () -> service().gerarToken("code", "state-a"));
        verifyNoInteractions(repository, restTemplate);
    }

    @Test
    void conflitoConcorrenteRetornaCodigoDeDominioSemErroSql() {
        Empresa empresa = Empresa.builder().id("empresa-b").build();
        User gestor = User.builder().idUser("gestor-b").empresa(empresa).role(RoleUser.ADMIN).build();
        MercadoPagoTokenResponse token = new MercadoPagoTokenResponse(
                "token-b", "bearer", 21600L, "offline_access", "mp-concurrent",
                "refresh-b", "public", true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("oauth:mp:state-b")).thenReturn("gestor-b|empresa-b|false");
        when(userService.getUser("gestor-b")).thenReturn(gestor);
        when(restTemplate.postForEntity(anyString(), any(), eq(MercadoPagoTokenResponse.class)))
                .thenReturn(ResponseEntity.ok(token));
        doThrow(new DataIntegrityViolationException("lease concorrente"))
                .when(accountLifecycleService).autorizar("empresa-b", token, false);
        doThrow(new ApiBusinessException(HttpStatus.CONFLICT,
                "MERCADO_PAGO_ACCOUNT_ALREADY_LINKED",
                "Esta conta Mercado Pago já está vinculada a outra empresa no sistema."))
                .when(accountLifecycleService).reautorizarAposConflito("empresa-b", token);

        ApiBusinessException error = assertThrows(ApiBusinessException.class,
                () -> service().gerarToken("code-b", "state-b"));

        assertEquals("MERCADO_PAGO_ACCOUNT_ALREADY_LINKED", error.getCode());
        assertFalse(error.getMessage().contains("lease concorrente"));
        verify(accountLifecycleService).registrarConflito("empresa-b");
        verify(valueOperations).set(eq("oauth:mp:result:empresa-b"),
                eq("MERCADO_PAGO_ACCOUNT_ALREADY_LINKED"), any(java.time.Duration.class));
        verify(redisTemplate).delete("oauth:mp:state-b");
    }

    @Test
    void resultadoOauthEhTenantScopedTemporarioESemIdentidadeDaOutraEmpresa() {
        EmpresaContext.set("empresa-b");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("oauth:mp:result:empresa-b"))
                .thenReturn("MERCADO_PAGO_ACCOUNT_ALREADY_LINKED");

        var result = service().consultarResultado();

        assertEquals("MERCADO_PAGO_ACCOUNT_ALREADY_LINKED", result.code());
        assertEquals("Esta conta Mercado Pago já está vinculada a outra empresa no sistema.",
                result.message());
        verify(redisTemplate, never()).delete("oauth:mp:result:empresa-b");
    }

    @Test
    @SuppressWarnings("unchecked")
    void renovarTokenEnviaGrantRefreshTokenSemRedirectUri() {
        MercadoPagoTokenResponse token = new MercadoPagoTokenResponse(
                "novo", "bearer", 15552000L, "offline_access", "mp-user-a", "refresh-2", "public", true);
        when(restTemplate.postForEntity(anyString(), any(), eq(MercadoPagoTokenResponse.class)))
                .thenReturn(ResponseEntity.ok(token));

        assertSame(token, service().renovarToken("refresh-1"));

        ArgumentCaptor<HttpEntity<MultiValueMap<String, String>>> request = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(eq("https://api.mercadopago.com/oauth/token"), request.capture(),
                eq(MercadoPagoTokenResponse.class));
        MultiValueMap<String, String> body = request.getValue().getBody();
        assertEquals("refresh_token", body.getFirst("grant_type"));
        assertEquals("refresh-1", body.getFirst("refresh_token"));
        assertEquals("client", body.getFirst("client_id"));
        assertFalse(body.containsKey("redirect_uri"));
    }

    @Test
    void renovarTokenRecusadoViraCodigoDeDominio() {
        when(restTemplate.postForEntity(anyString(), any(), eq(MercadoPagoTokenResponse.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request",
                        null, "{\"error\":\"invalid_grant\"}".getBytes(), null));

        ApiBusinessException error = assertThrows(ApiBusinessException.class,
                () -> service().renovarToken("refresh-revogado"));
        assertEquals("MERCADO_PAGO_REFRESH_REJECTED", error.getCode());
    }

    @Test
    void renovarTokenComFalhaDeRedeNaoEhRecusa() {
        when(restTemplate.postForEntity(anyString(), any(), eq(MercadoPagoTokenResponse.class)))
                .thenThrow(new ResourceAccessException("timeout"));

        assertThrows(ExternalServiceException.class, () -> service().renovarToken("refresh-1"));
    }

    private OauthMercadoPagoService service() {
        OauthMercadoPagoService service = new OauthMercadoPagoService(
                redisTemplate, userService, repository, restTemplate);
        ReflectionTestUtils.setField(service, "clientId", "client");
        ReflectionTestUtils.setField(service, "clientSecret", "secret");
        ReflectionTestUtils.setField(service, "redirectUri", "https://example.test/callback");
        ReflectionTestUtils.setField(service, "accountLifecycleService", accountLifecycleService);
        return service;
    }
}
