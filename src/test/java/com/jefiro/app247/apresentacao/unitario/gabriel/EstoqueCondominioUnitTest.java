package com.jefiro.app247.apresentacao.unitario.gabriel;

import com.jefiro.app247.domain.model.*;
import com.jefiro.app247.domain.model.enum_type.ProdutoCatalogChangeReason;
import com.jefiro.app247.domain.model.enum_type.TipoMovimentacaoEstoque;
import com.jefiro.app247.domain.model.enum_type.UnidadeMedida;
import com.jefiro.app247.domain.model.terminal.Terminal;
import com.jefiro.app247.infra.event.ProdutoCatalogChangedEvent;
import com.jefiro.app247.infra.repository.EstoqueCondominioRepository;
import com.jefiro.app247.infra.repository.MovimentacaoEstoqueRepository;
import com.jefiro.app247.infra.service.AuditLogService;
import com.jefiro.app247.infra.service.CondominioService;
import com.jefiro.app247.infra.service.EmpresaContext;
import com.jefiro.app247.infra.service.EstoqueService;
import com.jefiro.app247.infra.service.ProdutoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Testes unitários complementares do estoque do condomínio ({@link EstoqueService}):
 * entrada manual, ajuste, disponibilização do produto no condomínio, nova reserva
 * (retry de pagamento) e liberação sem cancelamento.
 *
 * <p>Complementa {@code VendaEstoqueUnitTest} (fluxo de venda) e {@code EstoqueServiceTest}.
 * Os repositórios são mocks: nenhum banco é usado.
 */
@ExtendWith(MockitoExtension.class)
class EstoqueCondominioUnitTest {

    @Mock EstoqueCondominioRepository estoqueRepository;
    @Mock MovimentacaoEstoqueRepository movimentacaoRepository;
    @Mock CondominioService condominioService;
    @Mock ProdutoService produtoService;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock AuditLogService auditLogService;
    @InjectMocks EstoqueService service;

    private final Set<String> chavesGravadas = new HashSet<>();
    private Empresa empresa;
    private Condominio condominio;
    private Produto produto;

    @BeforeEach
    void cenarioBase() {
        EmpresaContext.set("empresa-a");
        empresa = Empresa.builder().id("empresa-a").build();
        condominio = new Condominio();
        condominio.setIdCondominio("cond-a");
        condominio.setEmpresa(empresa);
        produto = new Produto();
        produto.setIdProduto("prod-a");
        produto.setNome("Refrigerante");
        produto.setEmpresa(empresa);
        produto.setCodigoInterno("SKU-A");
        produto.setUnidadeMedida(UnidadeMedida.UN);
    }

    @AfterEach
    void limparTenant() {
        EmpresaContext.clear();
    }

    // ------------------------------------------------------------------ entrada

    @Test
    @DisplayName("Entrada manual soma ao saldo e registra movimentação ENTRADA com saldo anterior e posterior")
    void entradaSomaAoSaldo() {
        EstoqueCondominio estoque = estoque("5.000", true);
        when(estoqueRepository.findForUpdate("cond-a", "prod-a")).thenReturn(Optional.of(estoque));

        var resposta = service.entrada("cond-a", "prod-a", new BigDecimal("3.000"), "Reposição");

        assertThat(resposta.quantidade()).isEqualByComparingTo("8.000");
        assertThat(estoque.getQuantidade()).isEqualByComparingTo("8.000");

        MovimentacaoEstoque mov = movimentoSalvo();
        assertThat(mov.getTipo()).isEqualTo(TipoMovimentacaoEstoque.ENTRADA);
        assertThat(mov.getQuantidade()).isEqualByComparingTo("3.000");
        assertThat(mov.getQuantidadeAnterior()).isEqualByComparingTo("5.000");
        assertThat(mov.getQuantidadePosterior()).isEqualByComparingTo("8.000");
        assertThat(mov.getMotivo()).isEqualTo("Reposição");
        verify(auditLogService).record(any(), eq("STOCK_ENTRY"), eq("EstoqueCondominio"),
                any(), any(), any(), any());
    }

    @Test
    @DisplayName("Entrada negativa é recusada e não grava nada")
    void entradaNegativaERecusada() {
        assertThatThrownBy(() -> service.entrada("cond-a", "prod-a", new BigDecimal("-1.000"), "Erro"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Entrada deve ser positiva");

        verifyNoInteractions(estoqueRepository, movimentacaoRepository, auditLogService);
    }

    @Test
    @DisplayName("Entrada de zero é aceita hoje e não altera o saldo (comportamento atual)")
    void entradaZeroNaoAlteraSaldo() {
        EstoqueCondominio estoque = estoque("5.000", true);
        when(estoqueRepository.findForUpdate("cond-a", "prod-a")).thenReturn(Optional.of(estoque));

        service.entrada("cond-a", "prod-a", BigDecimal.ZERO, "Zero");

        assertThat(estoque.getQuantidade()).isEqualByComparingTo("5.000");
    }

    @Test
    @DisplayName("Entrada em produto que não está no condomínio é recusada")
    void entradaEmProdutoSemEstoque() {
        when(estoqueRepository.findForUpdate("cond-a", "prod-a")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.entrada("cond-a", "prod-a", BigDecimal.TEN, "Compra"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Produto não está disponível no condomínio");

        verify(movimentacaoRepository, never()).save(any());
    }

    // ------------------------------------------------------------------- ajuste

    @Test
    @DisplayName("Ajuste para um saldo menor grava movimentação AJUSTE negativa")
    void ajusteParaMenos() {
        EstoqueCondominio estoque = estoque("10.000", true);
        when(estoqueRepository.findForUpdate("cond-a", "prod-a")).thenReturn(Optional.of(estoque));

        var resposta = service.ajustar("cond-a", "prod-a", new BigDecimal("4.000"), "Inventário");

        assertThat(resposta.quantidade()).isEqualByComparingTo("4.000");
        MovimentacaoEstoque mov = movimentoSalvo();
        assertThat(mov.getTipo()).isEqualTo(TipoMovimentacaoEstoque.AJUSTE);
        assertThat(mov.getQuantidade()).isEqualByComparingTo("-6.000");
        assertThat(mov.getQuantidadeAnterior()).isEqualByComparingTo("10.000");
        assertThat(mov.getQuantidadePosterior()).isEqualByComparingTo("4.000");
        verify(auditLogService).record(any(), eq("STOCK_ADJUSTED"), eq("EstoqueCondominio"),
                any(), any(), any(), any());
    }

    @Test
    @DisplayName("Ajuste para um saldo maior grava movimentação AJUSTE positiva")
    void ajusteParaMais() {
        EstoqueCondominio estoque = estoque("2.000", true);
        when(estoqueRepository.findForUpdate("cond-a", "prod-a")).thenReturn(Optional.of(estoque));

        service.ajustar("cond-a", "prod-a", new BigDecimal("9.500"), "Contagem");

        assertThat(estoque.getQuantidade()).isEqualByComparingTo("9.500");
        assertThat(movimentoSalvo().getQuantidade()).isEqualByComparingTo("7.500");
    }

    // ---------------------------------------------------------- disponibilizar

    @Test
    @DisplayName("Disponibilizar produto novo cria o estoque com a quantidade inicial")
    void disponibilizaProdutoNovoComQuantidadeInicial() {
        prepararDisponibilizacao(Optional.empty());
        when(estoqueRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        var resposta = service.disponibilizar("cond-a", "prod-a", new BigDecimal("12.000"));

        assertThat(resposta.quantidade()).isEqualByComparingTo("12.000");
        assertThat(resposta.ativo()).isTrue();
        MovimentacaoEstoque mov = movimentoSalvo();
        assertThat(mov.getTipo()).isEqualTo(TipoMovimentacaoEstoque.ENTRADA);
        assertThat(mov.getMotivo()).isEqualTo("Estoque inicial");
        assertThat(mov.getQuantidadeAnterior()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Disponibilizar produto já ativo no condomínio é recusado")
    void disponibilizarProdutoJaAtivo() {
        prepararDisponibilizacao(Optional.of(estoque("3.000", true)));

        assertThatThrownBy(() -> service.disponibilizar("cond-a", "prod-a", BigDecimal.ONE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Produto já está disponível no condomínio");

        verify(movimentacaoRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Reativar produto removido soma a quantidade ao saldo antigo e notifica o terminal")
    void reativaProdutoRemovido() {
        EstoqueCondominio inativo = estoque("2.000", false);
        prepararDisponibilizacao(Optional.of(inativo));

        var resposta = service.disponibilizar("cond-a", "prod-a", new BigDecimal("5.000"));

        assertThat(resposta.quantidade()).isEqualByComparingTo("7.000");
        assertThat(inativo.getAtivo()).isTrue();
        assertThat(movimentoSalvo().getMotivo()).isEqualTo("Reativação no condomínio");
        ArgumentCaptor<ProdutoCatalogChangedEvent> evento = ArgumentCaptor.forClass(ProdutoCatalogChangedEvent.class);
        verify(eventPublisher).publishEvent(evento.capture());
        assertThat(evento.getValue().reason()).isEqualTo(ProdutoCatalogChangeReason.PRODUCT_AVAILABILITY_CHANGED);
    }

    @Test
    @DisplayName("Disponibilizar como inativo cria o estoque sem notificar o terminal")
    void disponibilizarInativoNaoNotifica() {
        prepararDisponibilizacao(Optional.empty());
        when(estoqueRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        var resposta = service.disponibilizar("cond-a", "prod-a", BigDecimal.TEN, false);

        assertThat(resposta.ativo()).isFalse();
        verifyNoInteractions(eventPublisher);
    }

    // ---------------------------------------------- reserva (retry) e liberação

    @Test
    @DisplayName("Nova reserva só é aceita a partir da segunda tentativa de pagamento")
    void retryExigeTentativaPosterior() {
        Fixture f = fixtureDePedido("10.000", "2.000");

        assertThatThrownBy(() -> service.reservarRetry(f.order(), 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Retry deve usar tentativa posterior à primeira");

        verifyNoInteractions(estoqueRepository, movimentacaoRepository);
    }

    @Test
    @DisplayName("Nova reserva na tentativa 2 baixa o estoque com chave de idempotência própria")
    void retryReservaNovamente() {
        Fixture f = fixtureDePedido("10.000", "2.000");
        simularRepositorio(f.estoque());

        service.reservarRetry(f.order(), 2);

        assertThat(f.estoque().getQuantidade()).isEqualByComparingTo("8.000");
        MovimentacaoEstoque mov = movimentoSalvo();
        assertThat(mov.getTipo()).isEqualTo(TipoMovimentacaoEstoque.RESERVA);
        assertThat(mov.getChaveIdempotencia()).isEqualTo("order-a:item-a:RESERVA:2");
    }

    @Test
    @DisplayName("Pagamento não concluído libera a reserva como LIBERACAO_RESERVA e devolve o saldo")
    void liberacaoSemCancelamento() {
        Fixture f = fixtureDePedido("10.000", "2.000");
        simularRepositorio(f.estoque());
        service.reservar(f.order());
        assertThat(f.estoque().getQuantidade()).isEqualByComparingTo("8.000");

        service.liberar(f.order(), false);

        assertThat(f.estoque().getQuantidade()).isEqualByComparingTo("10.000");
        ArgumentCaptor<MovimentacaoEstoque> captor = ArgumentCaptor.forClass(MovimentacaoEstoque.class);
        verify(movimentacaoRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(1).getTipo()).isEqualTo(TipoMovimentacaoEstoque.LIBERACAO_RESERVA);
        assertThat(captor.getAllValues().get(1).getMotivo()).isEqualTo("Pagamento não concluído");
    }

    @Test
    @DisplayName("Liberar duas vezes o mesmo pedido devolve as unidades uma única vez")
    void liberacaoDuplicadaNaoDevolveDuasVezes() {
        Fixture f = fixtureDePedido("10.000", "2.000");
        simularRepositorio(f.estoque());
        service.reservar(f.order());

        service.liberar(f.order(), false);
        service.liberar(f.order(), false);

        assertThat(f.estoque().getQuantidade()).isEqualByComparingTo("10.000");
    }

    @Test
    @DisplayName("Pagamento aprovado sem reserva prévia não altera o estoque")
    void confirmarVendaSemReserva() {
        Fixture f = fixtureDePedido("10.000", "2.000");

        service.confirmarVenda(f.order());

        assertThat(f.estoque().getQuantidade()).isEqualByComparingTo("10.000");
        verify(movimentacaoRepository, never()).save(any());
        verify(estoqueRepository, never()).findForUpdate(anyString(), anyString());
    }

    // ------------------------------------------------------------------ apoio

    private MovimentacaoEstoque movimentoSalvo() {
        ArgumentCaptor<MovimentacaoEstoque> captor = ArgumentCaptor.forClass(MovimentacaoEstoque.class);
        verify(movimentacaoRepository).save(captor.capture());
        return captor.getValue();
    }

    private void prepararDisponibilizacao(Optional<EstoqueCondominio> existente) {
        when(condominioService.buscarDoTenant("cond-a", "empresa-a")).thenReturn(condominio);
        when(produtoService.buscarPorIdDoTenant("prod-a", "empresa-a")).thenReturn(produto);
        when(estoqueRepository.findByCondominioIdCondominioAndProdutoIdProduto("cond-a", "prod-a"))
                .thenReturn(existente);
    }

    /** Repositórios "lembram" as chaves gravadas, como a restrição de unicidade do banco real. */
    private void simularRepositorio(EstoqueCondominio estoque) {
        lenient().when(estoqueRepository.findForUpdate("cond-a", "prod-a")).thenReturn(Optional.of(estoque));
        lenient().when(movimentacaoRepository.existsByChaveIdempotencia(anyString()))
                .thenAnswer(inv -> chavesGravadas.contains(inv.<String>getArgument(0)));
        lenient().when(movimentacaoRepository.save(any(MovimentacaoEstoque.class))).thenAnswer(inv -> {
            MovimentacaoEstoque m = inv.getArgument(0);
            chavesGravadas.add(m.getChaveIdempotencia());
            return m;
        });
    }

    private EstoqueCondominio estoque(String saldo, boolean ativo) {
        EstoqueCondominio estoque = new EstoqueCondominio();
        estoque.setEmpresa(empresa);
        estoque.setCondominio(condominio);
        estoque.setProduto(produto);
        estoque.setQuantidade(new BigDecimal(saldo));
        estoque.setAtivo(ativo);
        return estoque;
    }

    private Fixture fixtureDePedido(String saldo, String quantidadePedida) {
        Terminal terminal = new Terminal();
        terminal.setIdTerminal("terminal-a");
        terminal.setCondominio(condominio);
        CartItem item = new CartItem();
        item.setIdItem("item-a");
        item.setProduto(produto);
        item.setQuantity(new BigDecimal(quantidadePedida));
        item.setName("Refrigerante");
        item.setUnitPrice(new BigDecimal("5.000000"));
        item.setOriginalPrice(new BigDecimal("5.000000"));
        item.setCalculatedDiscount(BigDecimal.ZERO.setScale(6));
        item.setCalculatedSubtotal(new BigDecimal("10.000000"));
        item.setUnidadeMedida(UnidadeMedida.UN);
        Carrinho carrinho = new Carrinho();
        carrinho.setTerminal(terminal);
        carrinho.setItems(List.of(item));
        item.setCarrinho(carrinho);
        Order order = new Order();
        order.setIdOrder("order-a");
        order.setEmpresa(empresa);
        order.setCondominio(condominio);
        order.setTerminal(terminal);
        order.setCarrinho(carrinho);
        OrderItem orderItem = OrderItem.snapshot(order, item);
        orderItem.setId("item-a");
        order.setItems(List.of(orderItem));
        return new Fixture(order, estoque(saldo, true));
    }

    private record Fixture(Order order, EstoqueCondominio estoque) {}
}
