package com.jefiro.app247.apresentacao.unitario.gabriel;

import com.jefiro.app247.apresentacao.integracao.IntegracaoBase;
import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.Produto;
import com.jefiro.app247.domain.model.auth.RoleUser;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.enum_type.ProdutoCategoria;
import com.jefiro.app247.domain.model.enum_type.UnidadeMedida;
import com.jefiro.app247.infra.repository.EstoqueEmpresaRepository;
import com.jefiro.app247.infra.repository.ProdutoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Compra (entrada de mercadoria) e venda/saída no estoque central da empresa
 * ({@code /estoque-empresa/{produtoId}/entrada|saida}), conferindo o saldo gravado no banco
 * e o histórico de movimentações após cada operação.
 */
class CompraVendaEstoqueIntegrationTest extends IntegracaoBase {

    // @Autowired: o Spring injeta os repositórios reais (ligados ao H2 do teste).
    // Cria o produto movimentado no cenário.
    @Autowired ProdutoRepository produtos;
    // Lê o saldo do estoque central da empresa direto do banco, para conferir as operações.
    @Autowired EstoqueEmpresaRepository estoques;

    private User admin;
    private Produto produto;

    @BeforeEach
    void cenario() {
        Empresa empresa = novaEmpresa();
        admin = novoUsuario(empresa, "39053344705", RoleUser.ADMIN);
        Produto p = new Produto();
        p.setEmpresa(empresa);
        p.setCodigoInterno("AGUA-01");
        p.setNome("Água Mineral 500ml");
        p.setPreco(new BigDecimal("2.50"));
        p.setCategoria(ProdutoCategoria.BEBIDA);
        p.setUnidadeMedida(UnidadeMedida.UN);
        p.setStatus(true);
        produto = produtos.saveAndFlush(p);
    }

    @Test
    @DisplayName("Compra de 100 unidades e venda de 30 deixam saldo de 70")
    void compraEVendaAtualizamEstoque() throws Exception {
        movimentar("entrada", "100", "Compra do fornecedor")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantidade").value(100.0));

        movimentar("saida", "30", "Venda")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantidade").value(70.0));

        assertThat(saldoNoBanco()).isEqualByComparingTo("70.000");
    }

    @Test
    @DisplayName("Duas compras somam no estoque")
    void comprasSucessivasSomam() throws Exception {
        movimentar("entrada", "10", "Compra 1").andExpect(status().isOk());
        movimentar("entrada", "15.5", "Compra 2").andExpect(status().isOk());

        assertThat(saldoNoBanco()).isEqualByComparingTo("25.500");
    }

    @Test
    @DisplayName("Cada operação gera uma movimentação com saldo anterior e posterior")
    void historicoDeMovimentacoes() throws Exception {
        movimentar("entrada", "50", "Compra").andExpect(status().isOk());
        movimentar("saida", "8", "Venda").andExpect(status().isOk());

        mvc.perform(get("/estoque-empresa/movimentacoes").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.tipo == 'ENTRADA')].quantidadePosterior").value(50.0))
                .andExpect(jsonPath("$[?(@.tipo == 'SAIDA')].quantidadeAnterior").value(50.0))
                .andExpect(jsonPath("$[?(@.tipo == 'SAIDA')].quantidadePosterior").value(42.0));
    }

    @Test
    @DisplayName("Quantidade zero, negativa ou com mais de 3 casas decimais é recusada sem alterar o saldo")
    void quantidadesInvalidas() throws Exception {
        movimentar("entrada", "20", "Compra").andExpect(status().isOk());

        movimentar("entrada", "0", "Inválida").andExpect(status().isBadRequest());
        movimentar("saida", "-5", "Inválida").andExpect(status().isBadRequest());
        movimentar("entrada", "1.2345", "Inválida").andExpect(status().isBadRequest());

        assertThat(saldoNoBanco()).isEqualByComparingTo("20.000");
    }

    @Test
    @DisplayName("Usuário comum não pode movimentar estoque")
    void usuarioComumNaoMovimenta() throws Exception {
        User comum = novoUsuario(admin.getEmpresa(), "52998224725", RoleUser.USER);

        mvc.perform(post("/estoque-empresa/{id}/entrada", produto.getIdProduto())
                        .header("Authorization", bearer(comum)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantidade\":10}"))
                .andExpect(status().isForbidden());

        assertThat(estoques.findByEmpresaIdAndProdutoIdProduto(admin.getEmpresa().getId(),
                produto.getIdProduto())).isEmpty();
    }

    private org.springframework.test.web.servlet.ResultActions movimentar(
            String operacao, String quantidade, String motivo) throws Exception {
        return mvc.perform(post("/estoque-empresa/{id}/" + operacao, produto.getIdProduto())
                .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantidade\":" + quantidade + ",\"motivo\":\"" + motivo + "\"}"));
    }

    private BigDecimal saldoNoBanco() {
        return estoques.findByEmpresaIdAndProdutoIdProduto(admin.getEmpresa().getId(), produto.getIdProduto())
                .orElseThrow().getQuantidade();
    }
}
