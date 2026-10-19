package com.jefiro.app247.apresentacao.integracao.emily;

import com.jefiro.app247.apresentacao.integracao.IntegracaoBase;
import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.auth.RoleUser;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.infra.repository.EstoqueEmpresaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Integra autenticação, cadastro de produto e movimentação de estoque. */
class FluxoCompletoIntegrationTest extends IntegracaoBase {
    @Autowired EstoqueEmpresaRepository estoques;

    @Test
    @DisplayName("Administrador autentica, cadastra produto e movimenta estoque")
    void loginProdutoEEstoque() throws Exception {
        Empresa empresa = novaEmpresa();
        User admin = novoUsuario(empresa, "39053344705", RoleUser.ADMIN);

        String login = mvc.perform(post("/auth/login/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cpf\":\"39053344705\",\"senha\":\"" + SENHA + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String jwt = json.readTree(login).get("token").asText();
        String authorization = "Bearer " + jwt;

        String produto = mvc.perform(post("/produtos")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"FLUXO-01\",\"nome\":\"Arroz Integral\",\"preco\":12.90,\"unidadeMedida\":\"un\",\"categoria\":\"alimentos\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String id = json.readTree(produto).get("id").asText();

        mvc.perform(post("/estoque-empresa/{id}/entrada", id)
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantidade\":20,\"motivo\":\"Compra\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/estoque-empresa/{id}/saida", id)
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantidade\":7,\"motivo\":\"Venda\"}"))
                .andExpect(status().isOk());

        assertThat(estoques.findByEmpresaIdAndProdutoIdProduto(admin.getEmpresa().getId(), id)
                .orElseThrow().getQuantidade()).isEqualByComparingTo("13.000");
    }
}
