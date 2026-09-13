package com.jefiro.app247.infra.security;

import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.auth.RoleUser;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.infra.repository.UserRepository;
import com.jefiro.app247.infra.service.EmpresaContext;
import com.jefiro.app247.infra.service.TokenService;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecurityFilterTest {
    @Mock TokenService tokenService;
    @Mock UserRepository repository;

    private SecurityFilter filter;

    @BeforeEach
    void setUp() {
        filter = new SecurityFilter();
        filter.tokenService = tokenService;
        filter.repository = repository;
    }

    @AfterEach
    void clearContexts() {
        EmpresaContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void autenticaUsuarioEEmpresaAtivosSemAcessarEmpresaLazy() throws Exception {
        User user = user("user-a", "111", "empresa-a");
        givenToken("token-a", "111", "user-a", "empresa-a");
        when(repository.findSecurityIdentityByCpf("111")).thenReturn(Optional.of(
                new SecurityIdentity(user, "empresa-a", true, true, null)));

        AtomicBoolean chainCalled = new AtomicBoolean();
        filter.doFilterInternal(request("/admin/dashboard", "token-a"), new MockHttpServletResponse(),
                (request, response) -> {
                    chainCalled.set(true);
                    assertEquals("empresa-a", EmpresaContext.get());
                    assertSame(user, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
                });

        assertTrue(chainCalled.get());
        assertNull(EmpresaContext.get());
    }

    @Test
    void empresaDesativadaNaoAutenticaENaoAcessaProxy() throws Exception {
        User user = user("user-a", "111", "empresa-a");
        givenToken("token-a", "111", "user-a", "empresa-a");
        when(repository.findSecurityIdentityByCpf("111")).thenReturn(Optional.of(
                new SecurityIdentity(user, "empresa-a", true, false, null)));
        MockHttpServletRequest request = request("/admin/dashboard", "token-a");

        filter.doFilterInternal(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) -> {
            assertNull(EmpresaContext.get());
            assertNull(SecurityContextHolder.getContext().getAuthentication());
        });

        assertEquals("COMPANY_DISABLED", request.getAttribute(SecurityFilter.FAILURE_CODE_ATTRIBUTE));
        assertNull(EmpresaContext.get());
    }

    @Test
    void empresaEncerradaNaoAutentica() throws Exception {
        User user = user("user-a", "111", "empresa-a");
        givenToken("token-a", "111", "user-a", "empresa-a");
        when(repository.findSecurityIdentityByCpf("111")).thenReturn(Optional.of(
                new SecurityIdentity(user, "empresa-a", true, true, Instant.now())));
        MockHttpServletRequest request = request("/admin/dashboard", "token-a");

        filter.doFilterInternal(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) -> { });

        assertEquals("COMPANY_DISABLED", request.getAttribute(SecurityFilter.FAILURE_CODE_ATTRIBUTE));
        assertNull(EmpresaContext.get());
    }

    @Test
    void empresaDoTokenInexistenteOuDiferenteNaoAutentica() throws Exception {
        User user = user("user-a", "111", "empresa-atual");
        givenToken("token-a", "111", "user-a", "empresa-inexistente");
        when(repository.findSecurityIdentityByCpf("111")).thenReturn(Optional.of(
                new SecurityIdentity(user, "empresa-atual", true, true, null)));
        MockHttpServletRequest request = request("/admin/dashboard", "token-a");

        filter.doFilterInternal(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) -> { });

        assertEquals("COMPANY_NOT_FOUND", request.getAttribute(SecurityFilter.FAILURE_CODE_ATTRIBUTE));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(EmpresaContext.get());
    }

    @Test
    void jwtInvalidoPreservaFalhaAtualELimpaContexto() {
        when(tokenService.validateIdentity("invalid")).thenThrow(new RuntimeException("JWT inválido"));

        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> filter.doFilterInternal(request("/admin/dashboard", "invalid"),
                        new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) -> fail()));

        assertEquals("JWT inválido", failure.getMessage());
        assertNull(EmpresaContext.get());
        verifyNoInteractions(repository);
    }

    @Test
    void requestSemJwtContinuaSemExigirEmpresa() throws Exception {
        AtomicBoolean chainCalled = new AtomicBoolean();
        filter.doFilterInternal(new MockHttpServletRequest("GET", "/"), new MockHttpServletResponse(),
                (request, response) -> chainCalled.set(true));

        assertTrue(chainCalled.get());
        assertNull(EmpresaContext.get());
        verifyNoInteractions(tokenService, repository);
    }

    @Test
    void limpaEmpresaContextQuandoCadeiaLancaExcecao() {
        User user = user("user-a", "111", "empresa-a");
        givenToken("token-a", "111", "user-a", "empresa-a");
        when(repository.findSecurityIdentityByCpf("111")).thenReturn(Optional.of(
                new SecurityIdentity(user, "empresa-a", true, true, null)));

        assertThrows(ServletException.class,
                () -> filter.doFilterInternal(request("/admin/dashboard", "token-a"),
                        new MockHttpServletResponse(),
                        (request, response) -> {
                            assertEquals("empresa-a", EmpresaContext.get());
                            throw new ServletException("controller failure");
                        }));

        assertNull(EmpresaContext.get());
    }

    @Test
    void requisicoesSequenciaisNaoVazamTenant() throws ServletException, IOException {
        User first = user("user-a", "111", "empresa-a");
        User second = user("user-b", "222", "empresa-b");
        givenToken("token-a", "111", "user-a", "empresa-a");
        givenToken("token-b", "222", "user-b", "empresa-b");
        when(repository.findSecurityIdentityByCpf("111")).thenReturn(Optional.of(
                new SecurityIdentity(first, "empresa-a", true, true, null)));
        when(repository.findSecurityIdentityByCpf("222")).thenReturn(Optional.of(
                new SecurityIdentity(second, "empresa-b", true, true, null)));

        filter.doFilterInternal(request("/admin/dashboard", "token-a"), new MockHttpServletResponse(),
                (request, response) -> assertEquals("empresa-a", EmpresaContext.get()));
        assertNull(EmpresaContext.get());
        SecurityContextHolder.clearContext();

        filter.doFilterInternal(request("/admin/dashboard", "token-b"), new MockHttpServletResponse(),
                (request, response) -> assertEquals("empresa-b", EmpresaContext.get()));
        assertNull(EmpresaContext.get());
    }

    private void givenToken(String token, String subject, String userId, String empresaId) {
        when(tokenService.validateIdentity(token))
                .thenReturn(new TokenService.TokenIdentity(subject, userId, empresaId));
    }

    private MockHttpServletRequest request(String path, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private User user(String id, String cpf, String empresaId) {
        Empresa detachedProxy = new Empresa() {
            @Override public Boolean getAtivo() {
                throw new AssertionError("SecurityFilter não deve inicializar Empresa LAZY");
            }

            @Override public boolean isDefinitivamenteEncerrada() {
                throw new AssertionError("SecurityFilter não deve inicializar Empresa LAZY");
            }
        };
        detachedProxy.setId(empresaId);
        return User.builder()
                .idUser(id)
                .cpf(cpf)
                .senha("hash")
                .nome("Usuário")
                .email(id + "@example.com")
                .empresa(detachedProxy)
                .ativo(true)
                .role(RoleUser.ADMIN)
                .build();
    }
}
