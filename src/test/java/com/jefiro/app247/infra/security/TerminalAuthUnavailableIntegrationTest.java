package com.jefiro.app247.infra.security;

import com.jefiro.app247.infra.exception.ApiBusinessException;
import com.jefiro.app247.infra.service.TerminalCredentialAnomalyService;
import com.jefiro.app247.infra.service.TerminalRequestAuthorizationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Falha de infraestrutura não pode virar 401: o Terminal bloqueia a venda ao receber 401. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TerminalAuthUnavailableIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired TerminalHttpAuthenticationFilter filter;
    @MockitoBean TerminalRequestAuthorizationService authorization;
    @MockitoBean TerminalCredentialAnomalyService anomalies;

    @Test
    void falhaDeBancoNaAutenticacaoResponde503SemCodigoDeCredencial() throws Exception {
        when(authorization.authenticateHttp(anyString()))
                .thenThrow(new DataAccessResourceFailureException("db down"));

        mvc.perform(get("/produtos/home").header("X-Terminal-Token", "tdc_valida"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("TERMINAL_AUTH_UNAVAILABLE"));
    }

    @Test
    void credencialRecusadaContinuaRespondendo401() throws Exception {
        when(authorization.authenticateHttp(anyString())).thenThrow(new ApiBusinessException(
                HttpStatus.UNAUTHORIZED, "TERMINAL_AUTH_INVALID", "Credencial do Terminal inválida"));

        mvc.perform(get("/produtos/home").header("X-Terminal-Token", "tdc_revogada"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TERMINAL_AUTH_INVALID"));
    }

    @Test
    void falhaNoAlertaDeOrigemNaoDerrubaAutenticacao() throws Exception {
        when(authorization.authenticateHttp(anyString())).thenReturn(
                new AuthenticatedTerminal("terminal-a", "empresa-a", "condominio-a", 1));
        doThrow(new DataAccessResourceFailureException("audit down"))
                .when(anomalies).observe(any(), any());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/produtos/home");
        request.addHeader("X-Terminal-Token", "tdc_valida");
        MockFilterChain chain = new MockFilterChain();

        try {
            filter.doFilter(request, new MockHttpServletResponse(), chain);

            assertThat(request.getAttribute(SecurityFilter.FAILURE_CODE_ATTRIBUTE)).isNull();
            assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                    .isInstanceOf(AuthenticatedTerminal.class);
            assertThat(chain.getRequest()).isSameAs(request);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
