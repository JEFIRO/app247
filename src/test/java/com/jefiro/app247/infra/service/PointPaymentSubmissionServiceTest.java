package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.PaymentAttempt;
import com.jefiro.app247.domain.model.dto.OrderResponse;
import com.jefiro.app247.domain.model.dto.PointPaymentResponse;
import com.jefiro.app247.domain.model.enum_type.TerminalPaymentStatus;
import com.jefiro.app247.domain.model.enum_type.order.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PointPaymentSubmissionServiceTest {
    @Mock OrderService orderService;
    @Mock MercadoPagoCobrancaService mercadoPagoCobrancaService;
    @Mock PointPaymentPersistenceService persistenceService;
    @Mock PaymentStateTransitionService transitionService;

    PointPaymentSubmissionService service;
    Order order;

    @BeforeEach
    void setUp() {
        service = new PointPaymentSubmissionService();
        service.orderService = orderService;
        service.mercadoPagoCobrancaService = mercadoPagoCobrancaService;
        service.persistenceService = persistenceService;
        service.transitionService = transitionService;
        order = new Order();
        order.setIdOrder("order-a");
        order.setStatus(OrderStatus.PENDING);
        PaymentAttempt attempt = new PaymentAttempt(order);
        attempt.setIdPagamento("attempt-a");
        attempt.setExternalReference("attempt-a");
        order.setPagamento(attempt);
    }

    @Test
    void retryTecnicoAplicaRespostaTerminalDaMesmaTentativa() {
        OrderResponse remote = new OrderResponse(
                "mp-order-a", "point", "mp-user-a", "attempt-a", null, null,
                null, null, null, "processed", "accredited", null,
                "2026-08-30T20:00:00Z", 2, null,
                new OrderResponse.Transactions(List.of()));
        PointPaymentResponse current = currentResponse();
        when(orderService.getOrderForReconciliation("order-a")).thenReturn(order);
        when(persistenceService.markSubmissionStarted("order-a")).thenReturn(true);
        when(mercadoPagoCobrancaService.createRemoteOrder("order-a")).thenReturn(remote);
        when(persistenceService.current("order-a")).thenReturn(current);

        assertThat(service.submitSameAttempt("order-a", "STATUS_QUERY")).isSameAs(current);

        InOrder sequence = inOrder(mercadoPagoCobrancaService, persistenceService, transitionService);
        sequence.verify(mercadoPagoCobrancaService).createRemoteOrder("order-a");
        sequence.verify(persistenceService).persistRemoteAcceptance("order-a", remote);
        sequence.verify(transitionService).apply(any(MercadoPagoOrderState.class));
        sequence.verify(persistenceService).current("order-a");
    }

    @Test
    void remoteOrderJaConhecidaNuncaEhSubmetidaNovamente() {
        order.setMpOrderId("mp-order-a");
        PointPaymentResponse current = currentResponse();
        when(orderService.getOrderForReconciliation("order-a")).thenReturn(order);
        when(persistenceService.current("order-a")).thenReturn(current);

        assertThat(service.submitSameAttempt("order-a", "STATUS_QUERY")).isSameAs(current);

        verifyNoInteractions(mercadoPagoCobrancaService, transitionService);
        verify(persistenceService, never()).persistRemoteAcceptance(anyString(), any());
    }

    @Test
    void cemChamadasConcorrentesSubmetemUmaUnicaOperacaoRemota() throws Exception {
        OrderResponse remote = new OrderResponse(
                "mp-order-a", "point", "mp-user-a", "attempt-a", null, null,
                null, null, null, "created", "created", null,
                "2026-08-30T20:00:00Z", 1, null,
                new OrderResponse.Transactions(List.of()));
        PointPaymentResponse current = currentResponse();
        when(orderService.getOrderForReconciliation("order-a")).thenReturn(order);
        when(persistenceService.markSubmissionStarted("order-a")).thenReturn(true);
        when(mercadoPagoCobrancaService.createRemoteOrder("order-a")).thenReturn(remote);
        when(persistenceService.persistRemoteAcceptance("order-a", remote)).thenAnswer(call -> {
            order.setMpOrderId("mp-order-a");
            return current;
        });
        when(persistenceService.current("order-a")).thenReturn(current);

        var pool = Executors.newFixedThreadPool(12);
        try {
            List<Callable<PointPaymentResponse>> calls = new ArrayList<>();
            for (int index = 0; index < 100; index++) {
                calls.add(() -> service.submitSameAttempt("order-a", "CONCURRENT_TEST"));
            }
            var results = pool.invokeAll(calls);
            for (var result : results) {
                assertThat(result.get(2, TimeUnit.SECONDS)).isSameAs(current);
            }
        } finally {
            pool.shutdownNow();
        }

        verify(mercadoPagoCobrancaService, times(1)).createRemoteOrder("order-a");
        verify(persistenceService, times(1)).persistRemoteAcceptance("order-a", remote);
    }

    private PointPaymentResponse currentResponse() {
        return new PointPaymentResponse(
                "PAYMENT_STATUS", "order-a", "attempt-a", "terminal-a",
                TerminalPaymentStatus.WAITING_PAYMENT, OrderStatus.CREATED,
                null, null, "Aguardando pagamento");
    }
}
