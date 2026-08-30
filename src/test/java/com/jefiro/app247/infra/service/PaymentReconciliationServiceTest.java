package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Carrinho;
import com.jefiro.app247.domain.model.Condominio;
import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.Pagamento;
import com.jefiro.app247.domain.model.dto.OrderResponse;
import com.jefiro.app247.domain.model.enum_type.PagamentoStatus;
import com.jefiro.app247.domain.model.enum_type.order.OrderStatus;
import com.jefiro.app247.domain.model.terminal.Terminal;
import com.jefiro.app247.infra.repository.OrderRepository;
import com.jefiro.app247.infra.repository.PagamentoRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentReconciliationServiceTest {
    @Mock OrderService orderService;
    @Mock OrderRepository orderRepository;
    @Mock MercadoPagoOrderQueryService queryService;
    @Mock PaymentStateTransitionService transitionService;
    @Mock PagamentoRepository pagamentoRepository;
    @Mock CarrinhoService carrinhoService;
    @Mock ApplicationEventPublisher eventPublisher;

    PaymentReconciliationService service;
    Order order;

    @BeforeEach
    void setUp() {
        service = new PaymentReconciliationService();
        service.orderService = orderService;
        service.orderRepository = orderRepository;
        service.mercadoPagoOrderQueryService = queryService;
        service.transitionService = transitionService;
        service.windowHours = 4;
        service.batchSize = 100;

        Empresa empresa = new Empresa();
        empresa.setId("empresa-a");
        Condominio condominio = new Condominio();
        condominio.setEmpresa(empresa);
        Terminal terminal = new Terminal();
        terminal.setIdTerminal("terminal-a");
        terminal.setCondominio(condominio);
        Carrinho carrinho = new Carrinho();
        carrinho.setTerminal(terminal);
        order = new Order();
        order.setIdOrder("order-a");
        order.setEmpresa(empresa);
        order.setCarrinho(carrinho);
        order.setMpOrderId("mp-order-a");
        order.setStatus(OrderStatus.CREATED);
        Pagamento pagamento = new Pagamento();
        pagamento.setIdPagamento("payment-local-a");
        pagamento.setStatus(PagamentoStatus.PENDING);
        order.setPagamento(pagamento);
    }

    @Test
    void pendingRemotoAprovadoAplicaEstadoConsultado() {
        PaymentStateTransitionService realTransition = new PaymentStateTransitionService();
        realTransition.orderService = orderService;
        realTransition.carrinhoService = carrinhoService;
        realTransition.pagamentoRepository = pagamentoRepository;
        realTransition.eventPublisher = eventPublisher;
        service.transitionService = realTransition;
        when(orderService.getOrderForReconciliation("order-a")).thenReturn(order);
        when(orderService.getOrderForUpdate("order-a")).thenReturn(order);
        when(pagamentoRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(orderService.save(any())).thenAnswer(call -> call.getArgument(0));
        when(queryService.getOrderByEmpresa("empresa-a", "mp-order-a"))
                .thenReturn(remote("processed", "accredited"));

        assertThat(service.reconcileOrder("order-a")).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PROCESSED);
        assertThat(order.getPagamento().getStatus()).isEqualTo(PagamentoStatus.PROCESSED);
    }

    @Test
    void statusDefinitivoNaoConsultaMercadoPago() {
        order.setStatus(OrderStatus.PROCESSED);
        when(orderService.getOrderForReconciliation("order-a")).thenReturn(order);

        assertThat(service.reconcileOrder("order-a")).isFalse();
        verifyNoInteractions(queryService, transitionService);
    }

    @Test
    void endpointLocalAprovadoRetornaSemConsultaDesnecessaria() {
        order.setStatus(OrderStatus.PROCESSED);
        order.getPagamento().setStatus(PagamentoStatus.PROCESSED);
        when(orderService.getOrderForTerminal("order-a", "terminal-a")).thenReturn(order);

        var response = service.reconcileForTerminal("order-a", "terminal-a");

        assertThat(response.status().name()).isEqualTo("APPROVED");
        assertThat(response.reconciled()).isFalse();
        verifyNoInteractions(queryService, transitionService);
    }

    @Test
    void endpointPendenteComMercadoPagoIndisponivelNaoInventaFalha() {
        when(orderService.getOrderForTerminal("order-a", "terminal-a")).thenReturn(order);
        when(queryService.getOrderByEmpresa("empresa-a", "mp-order-a"))
                .thenThrow(new RuntimeException("offline"));

        var response = service.reconcileForTerminal("order-a", "terminal-a");

        assertThat(response.status().name()).isEqualTo("WAITING_PAYMENT");
        assertThat(response.reconciled()).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    void indisponibilidadeEmUmPagamentoNaoInterrompeLote() {
        when(orderRepository.findRecentReconciliationCandidateIds(anyList(), any(LocalDateTime.class), any()))
                .thenReturn(List.of("order-a", "order-b"));
        when(orderService.getOrderForReconciliation("order-a")).thenReturn(order);
        when(orderService.getOrderForReconciliation("order-b")).thenReturn(order);
        when(queryService.getOrderByEmpresa(anyString(), anyString()))
                .thenThrow(new RuntimeException("offline"))
                .thenReturn(remote("failed", "failed"));

        service.reconcileRecent("TEST");

        verify(queryService, times(2)).getOrderByEmpresa("empresa-a", "mp-order-a");
        verify(transitionService).apply(any());
    }

    private OrderResponse remote(String status, String detail) {
        return new OrderResponse(
                "mp-order-a", "point", "mp-user", "order-a", null, null, null,
                null, null, status, detail, null, "2026-08-28T01:00:00-03:00", 3,
                null, new OrderResponse.Transactions(List.of(
                        new OrderResponse.Payment("remote-payment-a", "10.00", status, detail, null))));
    }
}
