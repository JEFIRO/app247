package com.jefiro.app247.apresentacao.integracao.emily;

import com.jefiro.app247.apresentacao.integracao.IntegracaoBase;
import com.jefiro.app247.domain.model.auth.RoleUser;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.infra.repository.EstoqueEmpresaRepository;
import com.jefiro.app247.infra.repository.ProdutoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Exercita criação de produto via API e posterior entrada no estoque via API. */
class ProdutoEstoqueIntegrationTest extends IntegracaoBase {
    @Autowired ProdutoRepository produtos;
    @Autowired EstoqueEmpresaRepository estoques;
    private User admin;

    @BeforeEach
    void preparar() {
        admin = novoUsuario(novaEmpresa(), "39053344705", RoleUser.ADMIN);
    }

    @Test
    @DisplayName("Produto recém-cadastrado recebe entrada de estoque persistida")
    void cadastrarEMovimentar() throws Exception {
        String resposta = mvc.perform(post("/produtos")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"INTEG-EST-01\",\"nome\":\"Suco Natural\",\"preco\":8.50,\"unidadeMedida\":\"un\",\"categoria\":\"bebida\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String id = json.readTree(resposta).get("id").asText();
        assertThat(produtos.findById(id)).isPresent();

        mvc.perform(post("/estoque-empresa/{id}/entrada", id)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantidade\":12,\"motivo\":\"Reposição\"}"))
                .andExpect(status().isOk());

        assertThat(estoques.findByEmpresaIdAndProdutoIdProduto(admin.getEmpresa().getId(), id)
                .orElseThrow().getQuantidade()).isEqualByComparingTo("12.000");
    }
}
