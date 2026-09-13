package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Carrinho;
import com.jefiro.app247.domain.model.CartItem;
import com.jefiro.app247.domain.model.Condominio;
import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.OrderItem;
import com.jefiro.app247.domain.model.Produto;
import com.jefiro.app247.domain.model.enum_type.CarrinhoStatus;
import com.jefiro.app247.domain.model.enum_type.ItemStatus;
import com.jefiro.app247.domain.model.enum_type.UnidadeMedida;
import com.jefiro.app247.domain.model.terminal.Terminal;
import com.jefiro.app247.infra.event.OrderReservadaEvent;
import com.jefiro.app247.infra.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    @Mock OrderRepository repository;
    @Mock CarrinhoService carrinhoService;
    @Mock UserService userService;
    @Mock ProdutoService produtoService;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks OrderService service;

    @Test
    void recarregaProdutoCompletoAntesDePersistirSnapshotDaOrder() {
        Empresa empresa = Empresa.builder().id("empresa-a").build();
        Condominio condominio = new Condominio();
        condominio.setIdCondominio("condominio-a");
        condominio.setEmpresa(empresa);
        Terminal terminal = new Terminal();
        terminal.setIdTerminal("terminal-a");
        terminal.setCondominio(condominio);

        Produto produtoParcial = new Produto();
        produtoParcial.setIdProduto("produto-a");
        produtoParcial.setEmpresa(empresa);

        CartItem item = new CartItem();
        item.setProduto(produtoParcial);
        item.setEmpresa(empresa);
        item.setBarcode("789123");
        item.setName("Leite");
        item.setUnidadeMedida(UnidadeMedida.UN);
        item.setQuantity(BigDecimal.ONE);
        item.setOriginalPrice(new BigDecimal("7.500000"));
        item.setUnitPrice(new BigDecimal("7.500000"));
        item.setCalculatedDiscount(new BigDecimal("0.000000"));
        item.setCalculatedSubtotal(new BigDecimal("7.500000"));
        item.setStatus(ItemStatus.VALIDATED);

        Carrinho carrinho = new Carrinho();
        carrinho.setIdCarrinho("carrinho-a");
        carrinho.setEmpresa(empresa);
        carrinho.setTerminal(terminal);
        carrinho.setStatus(CarrinhoStatus.OPEN);
        carrinho.setSubtotal(new BigDecimal("7.500000"));
        carrinho.addItem(item);

        Produto produtoCompleto = new Produto();
        produtoCompleto.setIdProduto("produto-a");
        produtoCompleto.setEmpresa(empresa);
        produtoCompleto.setCodigoInterno("asdaw3");

        when(carrinhoService.getById("carrinho-a")).thenReturn(carrinho);
        when(repository.findByCarrinhoIdCarrinho("carrinho-a")).thenReturn(Optional.empty());
        when(produtoService.buscarPorIdDoTenant("produto-a", "empresa-a"))
                .thenReturn(produtoCompleto);
        doNothing().when(carrinhoService).reprecificarParaCheckout(carrinho);
        when(carrinhoService.save(carrinho)).thenReturn(carrinho);
        when(repository.saveAndFlush(any(Order.class))).thenAnswer(call -> call.getArgument(0));

        Order order = service.createOrder("carrinho-a", null);

        assertThat(item.getProduto()).isSameAs(produtoCompleto);
        assertThat(order.getItems()).hasSize(1);
        OrderItem snapshot = order.getItems().get(0);
        assertThat(snapshot.getCodigoInterno()).isEqualTo("asdaw3");
        assertThat(snapshot.getProduto()).isSameAs(produtoCompleto);
        assertThat(snapshot.getCodigoBarras()).isEqualTo("789123");
        assertThat(snapshot.getNome()).isEqualTo("Leite");
        assertThat(snapshot.getUnidadeMedida()).isEqualTo(UnidadeMedida.UN);
        assertThat(snapshot.getQuantidade()).isEqualByComparingTo("1");
        assertThat(snapshot.getPrecoOriginal()).isEqualByComparingTo("7.500000");
        assertThat(snapshot.getPrecoUnitarioAplicado()).isEqualByComparingTo("7.500000");
        assertThat(snapshot.getDescontoCalculado()).isEqualByComparingTo("0.000000");
        assertThat(snapshot.getSubtotalCalculado()).isEqualByComparingTo("7.500000");
        verify(repository).saveAndFlush(order);
        verify(eventPublisher).publishEvent(any(OrderReservadaEvent.class));
    }
}
