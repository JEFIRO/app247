package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.auth.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class TokenServiceTest {
    @Test
    void extraiSubjectUsuarioEEmpresaDoMesmoJwtValidado() {
        TokenService service = new TokenService();
        ReflectionTestUtils.setField(service, "senha", "test-only-jwt-secret-with-at-least-32-characters");
        Empresa empresa = new Empresa();
        empresa.setId("empresa-a");
        User user = User.builder().idUser("user-a").cpf("111").empresa(empresa).build();

        TokenService.TokenIdentity identity = service.validateIdentity(service.generateToken(user));

        assertEquals("111", identity.subject());
        assertEquals("user-a", identity.userId());
        assertEquals("empresa-a", identity.empresaId());
    }

    @Test
    void jwtInvalidoMantemFalhaDeValidacao() {
        TokenService service = new TokenService();
        ReflectionTestUtils.setField(service, "senha", "test-only-jwt-secret-with-at-least-32-characters");

        assertThrows(RuntimeException.class, () -> service.validateIdentity("invalid"));
    }
}
