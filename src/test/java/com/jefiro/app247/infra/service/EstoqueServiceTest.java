package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.*;
import com.jefiro.app247.domain.model.enum_type.TipoMovimentacaoEstoque;
import com.jefiro.app247.domain.model.terminal.Terminal;
import com.jefiro.app247.infra.repository.EstoqueCondominioRepository;
import com.jefiro.app247.infra.repository.MovimentacaoEstoqueRepository;
import com.jefiro.app247.infra.event.ProdutoCatalogChangedEvent;
import com.jefiro.app247.domain.model.enum_type.ProdutoCatalogChangeReason;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EstoqueServiceTest {
    @Mock EstoqueCondominioRepository estoqueRepository;
    @Mock MovimentacaoEstoqueRepository movimentacaoRepository;
    @Mock CondominioService condominioService;
    @Mock ProdutoService produtoService;
    @Mock TerminalService terminalService;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks EstoqueService service;

    @AfterEach
    void limparTenant() {
        EmpresaContext.clear();
    }

    @Test
    void reservaPermiteSaldoNegativoERegistraMovimento() {
        Fixture f = fixture("0.000");
        when(estoqueRepository.findForUpdate("cond-a", "prod-a")).thenReturn(Optional.of(f.estoque));

        service.reservar(f.order);

        assertEquals(new BigDecimal("-1.000"), f.estoque.getQuantidade());
        ArgumentCaptor<MovimentacaoEstoque> captor = ArgumentCaptor.forClass(MovimentacaoEstoque.class);
        verify(movimentacaoRepository).save(captor.capture());
        assertEquals(TipoMovimentacaoEstoque.RESERVA, captor.getValue().getTipo());
        assertEquals("order-a:item-a:RESERVA", captor.getValue().getChaveIdempotencia());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void webhookAprovadoDuplicadoNaoConfirmaVendaDuasVezes() {
        Fixture f = fixture("9.000");
        when(movimentacaoRepository.existsByChaveIdempotencia("order-a:item-a:VENDA")).thenReturn(true);

        service.confirmarVenda(f.order);

        verify(estoqueRepository, never()).findForUpdate(anyString(), anyString());
        verify(movimentacaoRepository, never()).save(any());
    }

    @Test
    void cancelamentoSemReservaNaoCriaEstoque() {
        Fixture f = fixture("4.000");

        service.liberar(f.order, true);

        assertEquals(new BigDecimal("4.000"), f.estoque.getQuantidade());
        verify(estoqueRepository, never()).save(any());
        verify(movimentacaoRepository, never()).save(any());
    }

    @Test
    void cancelamentoDuplicadoNaoDevolveDuasVezes() {
        Fixture f = fixture("4.000");
        when(movimentacaoRepository.existsByChaveIdempotencia("order-a:item-a:LIBERACAO")).thenReturn(true);

        service.liberar(f.order, true);

        verify(estoqueRepository, never()).findForUpdate(anyString(), anyString());
    }

    @Test
    void estoqueGeralUsaAgregacaoDoRepository() {
        EmpresaContext.set("empresa-a");
        when(estoqueRepository.somarPorProdutoDaEmpresa("empresa-a"))
                .thenReturn(List.<Object[]>of(new Object[]{"prod-a", "Leite", new BigDecimal("22.000")}));

        var resultado = service.listarGeral();

        assertEquals(new BigDecimal("22.000"), resultado.get(0).quantidade());
    }

    @Test
    void associacaoCriadaNotificaSomenteCondominioDaAssociacao() {
        EmpresaContext.set("empresa-a");
        Empresa empresa = Empresa.builder().id("empresa-a").build();
        Condominio condominio = new Condominio();
        condominio.setIdCondominio("cond-a");
        condominio.setEmpresa(empresa);
        Produto produto = new Produto();
        produto.setIdProduto("prod-a");
        produto.setEmpresa(empresa);
        when(condominioService.buscarDoTenant("cond-a", "empresa-a")).thenReturn(condominio);
        when(produtoService.buscarPorIdDoTenant("prod-a", "empresa-a")).thenReturn(produto);
        when(estoqueRepository.findByCondominioIdCondominioAndProdutoIdProduto("cond-a", "prod-a"))
                .thenReturn(Optional.empty());
        when(estoqueRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.disponibilizar("cond-a", "prod-a", BigDecimal.ZERO);

        ArgumentCaptor<ProdutoCatalogChangedEvent> captor =
                ArgumentCaptor.forClass(ProdutoCatalogChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertEquals(ProdutoCatalogChangeReason.PRODUCT_ASSIGNED_TO_CONDOMINIUM, captor.getValue().reason());
        assertEquals(Set.of("cond-a"), captor.getValue().condominiumIds());
    }

    @Test
    void remocaoFazSoftDeleteENotificaCondominio() {
        EmpresaContext.set("empresa-a");
        Fixture f = fixture("-1.000");
        f.estoque.setAtivo(true);
        when(condominioService.buscarDoTenant("cond-a", "empresa-a"))
                .thenReturn(f.estoque.getCondominio());
        when(produtoService.buscarPorIdDoTenant("prod-a", "empresa-a"))
                .thenReturn(f.estoque.getProduto());
        when(estoqueRepository.findForUpdate("cond-a", "prod-a")).thenReturn(Optional.of(f.estoque));

        service.remover("cond-a", "prod-a");

        assertFalse(f.estoque.getAtivo());
        assertEquals(new BigDecimal("-1.000"), f.estoque.getQuantidade());
        ArgumentCaptor<ProdutoCatalogChangedEvent> captor =
                ArgumentCaptor.forClass(ProdutoCatalogChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertEquals(ProdutoCatalogChangeReason.PRODUCT_REMOVED_FROM_CONDOMINIUM, captor.getValue().reason());
        assertEquals(Set.of("cond-a"), captor.getValue().condominiumIds());
    }

    private Fixture fixture(String quantidade) {
        Empresa empresa = Empresa.builder().id("empresa-a").build();
        Condominio condominio = new Condominio();
        condominio.setIdCondominio("cond-a");
        condominio.setEmpresa(empresa);
        Terminal terminal = new Terminal();
        terminal.setIdTerminal("terminal-a");
        terminal.setCondominio(condominio);
        Produto produto = new Produto();
        produto.setIdProduto("prod-a");
        produto.setEmpresa(empresa);
        Item item = new Item();
        item.setIdItem("item-a");
        item.setProduto(produto);
        item.setQuantity(1);
        Carrinho carrinho = new Carrinho();
        carrinho.setTerminal(terminal);
        carrinho.setItems(List.of(item));
        item.setCarrinho(carrinho);
        Order order = new Order();
        order.setIdOrder("order-a");
        order.setCarrinho(carrinho);
        EstoqueCondominio estoque = new EstoqueCondominio();
        estoque.setCondominio(condominio);
        estoque.setProduto(produto);
        estoque.setQuantidade(new BigDecimal(quantidade));
        return new Fixture(order, estoque);
    }

    private record Fixture(Order order, EstoqueCondominio estoque) {}
}
