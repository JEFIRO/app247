package com.jefiro.app247.apresentacao.integracao.emily;

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
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ajuste de saldo e isolamento entre empresas no estoque central
 * ({@code PUT /estoque-empresa/{produtoId}}), conferindo o saldo gravado no banco e o
 * histórico de movimentações. Complementa {@code CompraVendaEstoqueIntegrationTest}.
 */
class EstoqueAjusteIntegrationTest extends IntegracaoBase {

    @Autowired ProdutoRepository produtos;
    @Autowired EstoqueEmpresaRepository estoques;

    private Empresa empresa;
    private User admin;
    private Produto produto;

    @BeforeEach
    void cenario() {
        empresa = novaEmpresa();
        admin = novoUsuario(empresa, "39053344705", RoleUser.ADMIN);
        Produto p = new Produto();
        p.setEmpresa(empresa);
        p.setCodigoInterno("SUCO-01");
        p.setNome("Suco de Laranja 1L");
        p.setPreco(new BigDecimal("7.90"));
        p.setCategoria(ProdutoCategoria.BEBIDA);
        p.setUnidadeMedida(UnidadeMedida.UN);
        p.setStatus(true);
        produto = produtos.saveAndFlush(p);
    }

    @Test
    @DisplayName("Ajuste define o saldo exato e registra a diferença como AJUSTE")
    void ajusteDefineSaldoExato() throws Exception {
        enviar(post("/estoque-empresa/{id}/entrada", produto.getIdProduto()), admin, "100", "Compra")
                .andExpect(status().isOk());

        enviar(put("/estoque-empresa/{id}", produto.getIdProduto()), admin, "60", "Inventário")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantidade").value(60.0));

        assertThat(saldoNoBanco(empresa)).isEqualByComparingTo("60.000");

        mvc.perform(get("/estoque-empresa/movimentacoes").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.tipo == 'AJUSTE')].quantidade").value(-40.0))
                .andExpect(jsonPath("$[?(@.tipo == 'AJUSTE')].quantidadeAnterior").value(100.0))
                .andExpect(jsonPath("$[?(@.tipo == 'AJUSTE')].quantidadePosterior").value(60.0));
    }

    @Test
    @DisplayName("Ajuste com mais de 3 casas decimais é recusado e o saldo não muda")
    void ajusteComCasasDemais() throws Exception {
        enviar(post("/estoque-empresa/{id}/entrada", produto.getIdProduto()), admin, "20", "Compra")
                .andExpect(status().isOk());

        enviar(put("/estoque-empresa/{id}", produto.getIdProduto()), admin, "5.1234", "Inválido")
                .andExpect(status().isBadRequest());

        assertThat(saldoNoBanco(empresa)).isEqualByComparingTo("20.000");
    }

    @Test
    @DisplayName("Administrador de outra empresa não consegue movimentar o produto")
    void outraEmpresaNaoMovimenta() throws Exception {
        Empresa outra = novaEmpresa();
        User adminOutra = novoUsuario(outra, "52998224725", RoleUser.ADMIN);

        enviar(post("/estoque-empresa/{id}/entrada", produto.getIdProduto()), adminOutra, "10", "Indevida")
                .andExpect(status().isBadRequest());

        assertThat(estoques.findByEmpresaIdAndProdutoIdProduto(outra.getId(), produto.getIdProduto())).isEmpty();
        assertThat(estoques.findByEmpresaIdAndProdutoIdProduto(empresa.getId(), produto.getIdProduto())).isEmpty();
    }

    @Test
    @DisplayName("Movimentar produto que não existe retorna erro e não cria estoque")
    void produtoInexistente() throws Exception {
        enviar(post("/estoque-empresa/{id}/entrada", "nao-existe"), admin, "10", "Compra")
                .andExpect(status().isBadRequest());

        assertThat(estoques.findByEmpresaIdAndProdutoIdProduto(empresa.getId(), "nao-existe")).isEmpty();
    }

    private ResultActions enviar(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requisicao,
            User usuario, String quantidade, String motivo) throws Exception {
        return mvc.perform(requisicao.header("Authorization", bearer(usuario))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantidade\":" + quantidade + ",\"motivo\":\"" + motivo + "\"}"));
    }

    private BigDecimal saldoNoBanco(Empresa dona) {
        return estoques.findByEmpresaIdAndProdutoIdProduto(dona.getId(), produto.getIdProduto())
                .orElseThrow().getQuantidade();
    }
}
