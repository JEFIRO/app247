package com.jefiro.app247.infra.security;

import com.jefiro.app247.infra.service.admin.AdminDashboardService;
import com.jefiro.app247.domain.model.dto.EmpresaBrandingResponse;
import com.jefiro.app247.infra.service.EmpresaService;
import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.auth.RoleUser;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.infra.repository.UserRepository;
import com.jefiro.app247.infra.service.EmpresaContext;
import com.jefiro.app247.infra.service.TokenService;
import com.jefiro.app247.infra.service.OauthMercadoPagoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import org.springframework.http.MediaType;

import java.util.Optional;

@SpringBootTest(properties = "spring.jpa.open-in-view=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminSecurityTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean AdminDashboardService dashboardService;
    @MockitoBean EmpresaService empresaService;
    @MockitoBean TokenService tokenService;
    @MockitoBean UserRepository userRepository;
    @MockitoBean OauthMercadoPagoService oauthMercadoPagoService;

    @Test
    void endpointAdministrativoRejeitaChamadaAnonima() throws Exception {
        mockMvc.perform(get("/admin/dashboard")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void endpointAdministrativoRejeitaPapelOperacionalComum() throws Exception {
        mockMvc.perform(get("/admin/dashboard")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void endpointAdministrativoAceitaAdmin() throws Exception {
        mockMvc.perform(get("/admin/dashboard")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void endpointAdministrativoAceitaGerente() throws Exception {
        mockMvc.perform(get("/admin/dashboard")).andExpect(status().isOk());
    }

    @Test
    void brandingDaEmpresaRejeitaChamadaAnonima() throws Exception {
        mockMvc.perform(get("/empresas/me/branding")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void gerentePodeAtualizarBrandingComContratoValidado() throws Exception {
        when(empresaService.atualizarBranding(any())).thenReturn(new EmpresaBrandingResponse(
                "Mercado Parque", null, null, "#112233", "#445566", "#778899"));

        mockMvc.perform(put("/empresas/me/branding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nomeExibicao":"Mercado Parque","logoUrl":null,"logoDarkUrl":null,
                                 "corPrincipal":"#112233","corSecundaria":"#445566","corDestaque":"#778899"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void jwtComEmpresaAtivaAutenticaNormalmente() throws Exception {
        mockIdentity("active", "111", "user-a", "empresa-a", true);

        mockMvc.perform(get("/admin/dashboard").header("Authorization", "Bearer active"))
                .andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertNull(EmpresaContext.get());
    }

    @Test
    void jwtComEmpresaDesativadaRetornaErroEstruturado() throws Exception {
        mockIdentity("disabled", "111", "user-a", "empresa-a", false);

        mockMvc.perform(get("/admin/dashboard").header("Authorization", "Bearer disabled"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMPANY_DISABLED"));

        org.junit.jupiter.api.Assertions.assertNull(EmpresaContext.get());
    }

    @Test
    void endpointPublicoContinuaAcessivelComEmpresaDesativada() throws Exception {
        mockIdentity("disabled-public", "111", "user-a", "empresa-a", false);

        mockMvc.perform(get("/mercado-pago/oauth/callback")
                        .param("code", "authorization-code")
                        .param("state", "oauth-state")
                        .header("Authorization", "Bearer disabled-public"))
                .andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertNull(EmpresaContext.get());
    }

    @Test
    void jwtComEmpresaInexistenteRetornaErroEstruturado() throws Exception {
        Empresa empresaAtual = new Empresa();
        empresaAtual.setId("empresa-atual");
        User user = User.builder()
                .idUser("user-a").cpf("111").senha("hash").nome("Gestor")
                .email("user-a@example.com").empresa(empresaAtual).ativo(true).role(RoleUser.ADMIN)
                .build();
        when(tokenService.validateIdentity("missing-company")).thenReturn(
                new TokenService.TokenIdentity("111", "user-a", "empresa-removida"));
        when(userRepository.findSecurityIdentityByCpf("111")).thenReturn(Optional.of(
                new SecurityIdentity(user, "empresa-atual", true, true, null)));

        mockMvc.perform(get("/admin/dashboard").header("Authorization", "Bearer missing-company"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMPANY_NOT_FOUND"));

        org.junit.jupiter.api.Assertions.assertNull(EmpresaContext.get());
    }

    private void mockIdentity(String token, String cpf, String userId, String empresaId,
                              boolean empresaAtiva) {
        Empresa empresa = new Empresa();
        empresa.setId(empresaId);
        User user = User.builder()
                .idUser(userId)
                .cpf(cpf)
                .senha("hash")
                .nome("Gestor")
                .email(userId + "@example.com")
                .empresa(empresa)
                .ativo(true)
                .role(RoleUser.ADMIN)
                .build();
        when(tokenService.validateIdentity(token))
                .thenReturn(new TokenService.TokenIdentity(cpf, userId, empresaId));
        when(userRepository.findSecurityIdentityByCpf(cpf)).thenReturn(Optional.of(
                new SecurityIdentity(user, empresaId, true, empresaAtiva, null)));
    }
}
