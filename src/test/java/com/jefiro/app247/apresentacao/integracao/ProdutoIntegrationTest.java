package com.jefiro.app247.apresentacao.integracao;

import com.jefiro.app247.domain.model.Produto;
import com.jefiro.app247.domain.model.auth.RoleUser;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.enum_type.UnidadeMedida;
import com.jefiro.app247.infra.repository.ProdutoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cadastro ({@code POST /produtos}) e remoção ({@code PATCH /produtos/{id}/disponibilidade})
 * de produtos pelo administrador.
 *
 * <p>Os três tipos de produto cobertos são os três modos de venda do sistema:
 * por unidade ({@code UN}), por peso ({@code KG}, com peso e tolerância usados pela balança)
 * e por volume ({@code LITRO}). A remoção é lógica (produto desativado) para preservar o
 * histórico de vendas.
 */
class ProdutoIntegrationTest extends IntegracaoBase {

    // @Autowired: o Spring injeta o repositório real (ligado ao H2 do teste).
    // Confere no banco o produto cadastrado/desativado pelos endpoints.
    @Autowired ProdutoRepository produtos;
    private User admin;

    @BeforeEach
    void administrador() {
        admin = novoUsuario(novaEmpresa(), "39053344705", RoleUser.ADMIN);
    }

    @Test
    @DisplayName("Cadastra produto vendido por unidade")
    void cadastraProdutoPorUnidade() throws Exception {
        String id = cadastrar(produto("REFRI-01", "Refrigerante Lata", "5.50", "un", "bebida", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unidadeMedida").value("UN"))
                .andExpect(jsonPath("$.categoria").value("BEBIDA"))
                .andExpect(jsonPath("$.ativo").value(true))
                .andReturn().getResponse().getContentAsString();

        Produto salvo = produtos.findById(json.readTree(id).get("id").asText()).orElseThrow();
        assertThat(salvo.getNome()).isEqualTo("Refrigerante Lata");
        assertThat(salvo.getPreco()).isEqualByComparingTo("5.50");
        assertThat(salvo.getEmpresa().getId()).isEqualTo(admin.getEmpresa().getId());
    }

    @Test
    @DisplayName("Cadastra produto vendido por peso, com peso e tolerância")
    void cadastraProdutoPorPeso() throws Exception {
        cadastrar(produto("QUEIJO-01", "Queijo Mussarela", "45.90", "kg", "frios", "1.000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unidadeMedida").value("KG"))
                .andExpect(jsonPath("$.peso").value(1.0))
                .andExpect(jsonPath("$.pesoTolerancia").value(0.05));
    }

    @Test
    @DisplayName("Cadastra produto vendido por volume")
    void cadastraProdutoPorVolume() throws Exception {
        cadastrar(produto("LEITE-01", "Leite Integral", "6.29", "litro", "laticinios", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unidadeMedida").value(UnidadeMedida.LITRO.name()))
                .andExpect(jsonPath("$.categoria").value("LATICINIOS"));
    }

    @Test
    @DisplayName("Validações: nome vazio, preço zero, unidade inexistente e código repetido")
    void validacoesDeProduto() throws Exception {
        cadastrar(produto("X-01", "", "5.00", "un", "bebida", null)).andExpect(status().isBadRequest());
        cadastrar(produto("X-02", "Produto", "0", "un", "bebida", null)).andExpect(status().isBadRequest());
        cadastrar(produto("X-03", "Produto", "5.00", "caixote", "bebida", null)).andExpect(status().isBadRequest());

        cadastrar(produto("X-04", "Produto", "5.00", "un", "bebida", null)).andExpect(status().isOk());
        cadastrar(produto("X-04", "Outro", "7.00", "un", "bebida", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_CODE_ALREADY_EXISTS"));

        assertThat(produtos.findAllByEmpresaId(admin.getEmpresa().getId(),
                org.springframework.data.domain.Pageable.unpaged()).getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("Remove (desativa) produto e ele continua no histórico como inativo")
    void removeProduto() throws Exception {
        String resposta = cadastrar(produto("PAO-01", "Pão de Forma", "8.90", "pacote", "padaria", null))
                .andReturn().getResponse().getContentAsString();
        String id = json.readTree(resposta).get("id").asText();

        mvc.perform(patch("/produtos/{id}/disponibilidade", id).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));

        assertThat(produtos.findById(id).orElseThrow().isStatus()).isFalse();
    }

    @Test
    @DisplayName("Remover produto inexistente retorna 404")
    void removeProdutoInexistente() throws Exception {
        mvc.perform(patch("/produtos/{id}/disponibilidade", "nao-existe").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":false}"))
                .andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.ResultActions cadastrar(String body) throws Exception {
        return mvc.perform(post("/produtos").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String produto(String codigo, String nome, String preco, String unidade,
                                  String categoria, String peso) {
        String pesoJson = peso == null ? "" : ",\"peso\":" + peso + ",\"pesoTolerancia\":0.050";
        return "{\"codigo\":\"" + codigo + "\",\"nome\":\"" + nome + "\",\"preco\":" + preco
                + ",\"unidadeMedida\":\"" + unidade + "\",\"categoria\":\"" + categoria + "\"" + pesoJson + "}";
    }
}
