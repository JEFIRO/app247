package com.jefiro.app247.apresentacao.integracao.emily;

import com.jefiro.app247.apresentacao.integracao.IntegracaoBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifica rejeição de credenciais ausentes e tokens inválidos em rota protegida. */
class SegurancaIntegrationTest extends IntegracaoBase {

    @Test
    @DisplayName("Rota protegida sem token retorna 401")
    void semToken() throws Exception {
        mvc.perform(get("/admin/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Token JWT malformado não autoriza acesso")
    void tokenMalformado() throws Exception {
        mvc.perform(get("/admin/dashboard")
                        .header("Authorization", "Bearer token-invalido"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Esquema de autorização incorreto não autoriza acesso")
    void esquemaIncorreto() throws Exception {
        mvc.perform(get("/admin/dashboard")
                        .header("Authorization", "Basic abc123"))
                .andExpect(status().isUnauthorized());
    }
}
