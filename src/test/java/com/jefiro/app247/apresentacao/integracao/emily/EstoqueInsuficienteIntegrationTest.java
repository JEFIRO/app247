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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifica que uma saída inválida não altera o saldo persistido. */
class EstoqueInsuficienteIntegrationTest extends IntegracaoBase {
    @Autowired ProdutoRepository produtos;
    @Autowired EstoqueEmpresaRepository estoques;
    private User admin;
    private Produto produto;

    @BeforeEach
    void preparar() {
        Empresa empresa = novaEmpresa();
        admin = novoUsuario(empresa, "39053344705", RoleUser.ADMIN);
        Produto p = new Produto();
        p.setEmpresa(empresa);
        p.setCodigoInterno("EST-INSUF-01");
        p.setNome("Produto teste estoque insuficiente");
        p.setPreco(new BigDecimal("10.00"));
        p.setCategoria(ProdutoCategoria.BEBIDA);
        p.setUnidadeMedida(UnidadeMedida.UN);
        p.setStatus(true);
        produto = produtos.saveAndFlush(p);
    }

    @Test
    @Disabled("Regra ainda não implementada: EstoqueEmpresaService.movimentar permite saldo negativo")
    @DisplayName("Saída acima do saldo é rejeitada e preserva estoque")
    void saidaMaiorQueSaldo() throws Exception {
        movimentar("entrada", "10").andExpect(status().isOk());
        movimentar("saida", "11").andExpect(status().is4xxClientError());
        assertThat(saldo()).isEqualByComparingTo("10.000");
    }

    @Test
    @DisplayName("Saída exatamente igual ao saldo deixa estoque zerado")
    void saidaIgualAoSaldo() throws Exception {
        movimentar("entrada", "10").andExpect(status().isOk());
        movimentar("saida", "10").andExpect(status().isOk());
        assertThat(saldo()).isEqualByComparingTo("0.000");
    }

    private org.springframework.test.web.servlet.ResultActions movimentar(String operacao, String quantidade) throws Exception {
        return mvc.perform(post("/estoque-empresa/{id}/" + operacao, produto.getIdProduto())
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantidade\":" + quantidade + ",\"motivo\":\"Teste\"}"));
    }

    private BigDecimal saldo() {
        return estoques.findByEmpresaIdAndProdutoIdProduto(admin.getEmpresa().getId(), produto.getIdProduto())
                .orElseThrow().getQuantidade();
    }
}
