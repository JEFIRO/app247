package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.MercadoPagoConta;
import com.jefiro.app247.domain.model.auth.RoleUser;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.dto.MercadoPagoTokenResponse;
import com.jefiro.app247.infra.repository.OauthMercadoPagoRepository;
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

    @AfterEach
    void limparContexto() {
        EmpresaContext.clear();
    }

    @Test
    void novoOauthAtualizaContaExistenteDaEmpresa() {
        Empresa empresa = Empresa.builder().id("empresa-a").build();
        User gestor = User.builder().idUser("gestor-a").empresa(empresa).role(RoleUser.ADMIN).build();
        MercadoPagoConta existente = MercadoPagoConta.builder().empresa(empresa).accessToken("antigo")
                .refreshToken("refresh-antigo").build();
        MercadoPagoTokenResponse token = new MercadoPagoTokenResponse(
                "novo", "bearer", 21600L, "offline_access", "mp-user-a",
                "refresh-novo", "public", true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("oauth:mp:state-a")).thenReturn("gestor-a|empresa-a");
        when(userService.getUser("gestor-a")).thenReturn(gestor);
        when(restTemplate.postForEntity(anyString(), any(), eq(MercadoPagoTokenResponse.class)))
                .thenReturn(ResponseEntity.ok(token));
        when(repository.findByEmpresaId("empresa-a")).thenReturn(Optional.of(existente));

        service().gerarToken("code", "state-a");

        assertEquals("novo", existente.getAccessToken());
        assertEquals("refresh-novo", existente.getRefreshToken());
        verify(repository).save(existente);
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

    private OauthMercadoPagoService service() {
        OauthMercadoPagoService service = new OauthMercadoPagoService(
                redisTemplate, userService, repository, restTemplate);
        ReflectionTestUtils.setField(service, "clientId", "client");
        ReflectionTestUtils.setField(service, "clientSecret", "secret");
        ReflectionTestUtils.setField(service, "redirectUri", "https://example.test/callback");
        return service;
    }
}
