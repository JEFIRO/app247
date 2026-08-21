package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Carrinho;
import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.terminal.Terminal;
import com.jefiro.app247.infra.repository.OrderRepository;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.*;

class OrderTerminalStatusTest {
    @Test
    void terminalCorretoPodeRecuperarEstadoEOutroTerminalNao() {
        OrderRepository repository = mock(OrderRepository.class);
        OrderService service = new OrderService();
        service.repository = repository;
        Terminal terminal = new Terminal();
        terminal.setIdTerminal("terminal-a");
        Carrinho carrinho = new Carrinho();
        carrinho.setTerminal(terminal);
        Order order = new Order();
        order.setIdOrder("order-a");
        order.setCarrinho(carrinho);
        when(repository.findById("order-a")).thenReturn(Optional.of(order));

        assertThat(service.getOrderForTerminal("order-a", "terminal-a")).isSameAs(order);
        assertThatThrownBy(() -> service.getOrderForTerminal("order-a", "terminal-b"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void cobrancaJaAtivaEhReutilizadaSemNovoEventoExterno() {
        OrderRepository repository = mock(OrderRepository.class);
        CarrinhoService carrinhoService = mock(CarrinhoService.class);
        org.springframework.context.ApplicationEventPublisher publisher =
                mock(org.springframework.context.ApplicationEventPublisher.class);
        OrderService service = new OrderService();
        service.repository = repository;
        service.carrinhoService = carrinhoService;
        service.eventPublisher = publisher;
        Carrinho carrinho = new Carrinho();
        carrinho.setIdCarrinho("cart-a");
        Order order = new Order();
        order.setMpOrderId("mp-order-a");
        when(repository.findByCarrinhoIdCarrinho("cart-a")).thenReturn(Optional.of(order));

        assertThat(service.criarCobranca(carrinho)).isSameAs(order);
        verify(carrinhoService).validarParaPagamento(carrinho);
        verifyNoInteractions(publisher);
    }
}
