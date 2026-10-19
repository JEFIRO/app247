package com.jefiro.app247.apresentacao.unitario.gabriel;

import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.EstoqueEmpresa;
import com.jefiro.app247.domain.model.MovimentacaoEstoque;
import com.jefiro.app247.domain.model.Produto;
import com.jefiro.app247.domain.model.enum_type.TipoMovimentacaoEstoque;
import com.jefiro.app247.domain.model.enum_type.UnidadeMedida;
import com.jefiro.app247.infra.repository.EmpresaRepository;
import com.jefiro.app247.infra.repository.EstoqueEmpresaRepository;
import com.jefiro.app247.infra.repository.MovimentacaoEstoqueRepository;
import com.jefiro.app247.infra.repository.ProdutoRepository;
import com.jefiro.app247.infra.service.AuditLogService;
import com.jefiro.app247.infra.service.EmpresaContext;
import com.jefiro.app247.infra.service.EstoqueEmpresaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Testes unitários do estoque central da empresa ({@link EstoqueEmpresaService}):
 * entrada (compra), saída (venda), ajuste de saldo e as validações de quantidade.
 * Repositórios e auditoria são mocks: nenhum banco é usado.
 */
@ExtendWith(MockitoExtension.class)
class EstoqueEmpresaUnitTest {

    @Mock EstoqueEmpresaRepository estoqueRepository;
    @Mock MovimentacaoEstoqueRepository movimentoRepository;
    @Mock ProdutoRepository produtoRepository;
    @Mock EmpresaRepository empresaRepository;
    @Mock AuditLogService auditLogService;
    @InjectMocks EstoqueEmpresaService service;

    private Empresa empresa;
    private Produto produto;

    @BeforeEach
    void cenario() {
        SecurityContextHolder.clearContext();
        EmpresaContext.set("empresa-a");
        empresa = Empresa.builder().id("empresa-a").build();
        produto = new Produto();
        produto.setIdProduto("prod-a");
        produto.setNome("Água Mineral 500ml");
        produto.setCodigoInterno("AGUA-01");
        produto.setUnidadeMedida(UnidadeMedida.UN);
        produto.setEmpresa(empresa);
    }

    @AfterEach
    void limpar() {
        EmpresaContext.clear();
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------ entrada

    @Test
    @DisplayName("Primeira entrada de um produto cria o estoque e grava o saldo")
    void primeiraEntradaCriaEstoque() {
        when(estoqueRepository.findForUpdate("empresa-a", "prod-a")).thenReturn(Optional.empty());
        when(produtoRepository.findByIdProdutoAndEmpresaId("prod-a", "empresa-a")).thenReturn(Optional.of(produto));
        when(empresaRepository.getReferenceById("empresa-a")).thenReturn(empresa);
        when(estoqueRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        var resposta = service.entrada("prod-a", new BigDecimal("40"), "Compra do fornecedor");

        assertThat(resposta.quantidade()).isEqualByComparingTo("40.000");
        assertThat(resposta.ativo()).isTrue();
        MovimentacaoEstoque mov = movimentoSalvo();
        assertThat(mov.getTipo()).isEqualTo(TipoMovimentacaoEstoque.ENTRADA);
        assertThat(mov.getQuantidadeAnterior()).isEqualByComparingTo("0.000");
        assertThat(mov.getQuantidadePosterior()).isEqualByComparingTo("40.000");
        assertThat(mov.getMotivo()).isEqualTo("Compra do fornecedor");
        verify(auditLogService).record(any(), eq("ESTOQUE_EMPRESA_ENTRADA"), eq("EstoqueEmpresa"),
                any(), any(), any(), any());
    }

    @Test
    @DisplayName("Entrada em estoque existente soma ao saldo atual")
    void entradaSomaAoSaldo() {
        EstoqueEmpresa estoque = estoque("10.000");
        when(estoqueRepository.findForUpdate("empresa-a", "prod-a")).thenReturn(Optional.of(estoque));

        var resposta = service.entrada("prod-a", new BigDecimal("15.500"), null);

        assertThat(resposta.quantidade()).isEqualByComparingTo("25.500");
        assertThat(movimentoSalvo().getQuantidade()).isEqualByComparingTo("15.500");
    }

    // ------------------------------------------------------------------- saída

    @Test
    @DisplayName("Saída reduz o saldo e grava movimentação SAIDA negativa")
    void saidaReduzSaldo() {
        EstoqueEmpresa estoque = estoque("50.000");
        when(estoqueRepository.findForUpdate("empresa-a", "prod-a")).thenReturn(Optional.of(estoque));

        var resposta = service.saida("prod-a", new BigDecimal("8"), "Venda");

        assertThat(resposta.quantidade()).isEqualByComparingTo("42.000");
        MovimentacaoEstoque mov = movimentoSalvo();
        assertThat(mov.getTipo()).isEqualTo(TipoMovimentacaoEstoque.SAIDA);
        assertThat(mov.getQuantidade()).isEqualByComparingTo("-8.000");
        assertThat(mov.getQuantidadeAnterior()).isEqualByComparingTo("50.000");
        assertThat(mov.getQuantidadePosterior()).isEqualByComparingTo("42.000");
    }

    @Test
    @DisplayName("Saída maior que o saldo deixa o estoque negativo (comportamento atual)")
    void saidaMaiorQueSaldoFicaNegativa() {
        EstoqueEmpresa estoque = estoque("5.000");
        when(estoqueRepository.findForUpdate("empresa-a", "prod-a")).thenReturn(Optional.of(estoque));

        service.saida("prod-a", new BigDecimal("8"), "Venda");

        assertThat(estoque.getQuantidade()).isEqualByComparingTo("-3.000");
    }

    // ------------------------------------------------------------------ ajuste

    @Test
    @DisplayName("Ajuste define o saldo exato e grava a diferença como AJUSTE")
    void ajusteDefineSaldoExato() {
        EstoqueEmpresa estoque = estoque("50.000");
        when(estoqueRepository.findForUpdate("empresa-a", "prod-a")).thenReturn(Optional.of(estoque));

        var resposta = service.ajustar("prod-a", new BigDecimal("35"), "Inventário");

        assertThat(resposta.quantidade()).isEqualByComparingTo("35.000");
        MovimentacaoEstoque mov = movimentoSalvo();
        assertThat(mov.getTipo()).isEqualTo(TipoMovimentacaoEstoque.AJUSTE);
        assertThat(mov.getQuantidade()).isEqualByComparingTo("-15.000");
    }

    @Test
    @DisplayName("Ajuste para zero zera o estoque")
    void ajusteParaZero() {
        EstoqueEmpresa estoque = estoque("12.000");
        when(estoqueRepository.findForUpdate("empresa-a", "prod-a")).thenReturn(Optional.of(estoque));

        service.ajustar("prod-a", BigDecimal.ZERO, "Produto vencido");

        assertThat(estoque.getQuantidade()).isEqualByComparingTo("0.000");
    }

    // ------------------------------------------------------------- validações

    @Test
    @DisplayName("Entrada com quantidade zero ou negativa é recusada")
    void entradaInvalida() {
        assertThatThrownBy(() -> service.entrada("prod-a", BigDecimal.ZERO, "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A quantidade de entrada deve ser maior que zero");
        assertThatThrownBy(() -> service.entrada("prod-a", new BigDecimal("-5"), "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A quantidade de entrada deve ser maior que zero");

        verifyNoInteractions(estoqueRepository, movimentoRepository, auditLogService);
    }

    @Test
    @DisplayName("Saída com quantidade zero ou negativa é recusada")
    void saidaInvalida() {
        assertThatThrownBy(() -> service.saida("prod-a", BigDecimal.ZERO, "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A quantidade de saída deve ser maior que zero");
        assertThatThrownBy(() -> service.saida("prod-a", new BigDecimal("-1"), "x"))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(estoqueRepository, movimentoRepository);
    }

    @Test
    @DisplayName("Quantidade com mais de 3 casas decimais é recusada")
    void maisDeTresCasasDecimais() {
        assertThatThrownBy(() -> service.entrada("prod-a", new BigDecimal("1.2345"), "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Quantidade aceita no máximo 3 casas decimais");
        assertThatThrownBy(() -> service.ajustar("prod-a", new BigDecimal("0.0001"), "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Quantidade aceita no máximo 3 casas decimais");
    }

    @Test
    @DisplayName("Quantidade nula é recusada")
    void quantidadeNula() {
        assertThatThrownBy(() -> service.entrada("prod-a", null, "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Quantidade obrigatória");
    }

    @Test
    @DisplayName("Produto de outra empresa não recebe estoque")
    void produtoDeOutraEmpresa() {
        when(estoqueRepository.findForUpdate("empresa-a", "prod-x")).thenReturn(Optional.empty());
        when(produtoRepository.findByIdProdutoAndEmpresaId("prod-x", "empresa-a")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.entrada("prod-x", BigDecimal.TEN, "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Produto não pertence à empresa atual");

        verify(estoqueRepository, never()).saveAndFlush(any());
        verify(movimentoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Sem empresa identificada na autenticação a operação é recusada (401)")
    void semEmpresaNoContexto() {
        EmpresaContext.clear();

        assertThatThrownBy(() -> service.entrada("prod-a", BigDecimal.TEN, "x"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(401));
        verify(movimentoRepository, never()).save(any());
    }

    // ------------------------------------------------------------------ apoio

    private MovimentacaoEstoque movimentoSalvo() {
        ArgumentCaptor<MovimentacaoEstoque> captor = ArgumentCaptor.forClass(MovimentacaoEstoque.class);
        verify(movimentoRepository).save(captor.capture());
        return captor.getValue();
    }

    private EstoqueEmpresa estoque(String saldo) {
        EstoqueEmpresa estoque = new EstoqueEmpresa();
        estoque.setEmpresa(empresa);
        estoque.setProduto(produto);
        estoque.setQuantidade(new BigDecimal(saldo));
        estoque.setAtivo(true);
        return estoque;
    }
}
