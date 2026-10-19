package com.jefiro.app247.apresentacao.integracao.emily;

import com.jefiro.app247.apresentacao.integracao.IntegracaoBase;
import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.auth.RoleUser;
import com.jefiro.app247.domain.model.auth.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifica isolamento de consultas de usuários entre empresas distintas. */
class IsolamentoEmpresaIntegrationTest extends IntegracaoBase {
    private User adminA;
    private User adminB;
    private User usuarioA;
    private User usuarioB;

    @BeforeEach
    void preparar() {
        Empresa a = novaEmpresa();
        Empresa b = novaEmpresa();
        adminA = novoUsuario(a, "39053344705", RoleUser.ADMIN);
        adminB = novoUsuario(b, "52998224725", RoleUser.ADMIN);
        usuarioA = novoUsuario(a, "11144477735", RoleUser.USER);
        usuarioB = novoUsuario(b, "93541134780", RoleUser.USER);
    }

    @Test
    @DisplayName("Administradores consultam usuários de suas respectivas empresas")
    void acessoDaPropriaEmpresa() throws Exception {
        mvc.perform(get("/user/{id}", usuarioA.getIdUser())
                .header("Authorization", bearer(adminA))).andExpect(status().isOk());
        mvc.perform(get("/user/{id}", usuarioB.getIdUser())
                .header("Authorization", bearer(adminB))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Administradores não enxergam usuários de outra empresa")
    void acessoEntreEmpresas() throws Exception {
        mvc.perform(get("/user/{id}", usuarioB.getIdUser())
                .header("Authorization", bearer(adminA))).andExpect(status().isNotFound());
        mvc.perform(get("/user/{id}", usuarioA.getIdUser())
                .header("Authorization", bearer(adminB))).andExpect(status().isNotFound());
    }
}
