package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.*;
import com.jefiro.app247.domain.model.enum_type.TipoMovimentacaoEstoque;
import com.jefiro.app247.domain.model.terminal.Terminal;
import com.jefiro.app247.infra.repository.EstoqueCondominioRepository;
import com.jefiro.app247.infra.repository.MovimentacaoEstoqueRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EstoqueServiceTest {
    @Mock EstoqueCondominioRepository estoqueRepository;
    @Mock MovimentacaoEstoqueRepository movimentacaoRepository;
    @Mock CondominioService condominioService;
    @Mock ProdutoService produtoService;
    @InjectMocks EstoqueService service;

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
        EmpresaContext.clear();
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
