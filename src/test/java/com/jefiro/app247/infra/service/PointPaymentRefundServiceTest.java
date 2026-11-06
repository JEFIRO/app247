package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.PaymentAttempt;
import com.jefiro.app247.domain.model.dto.MercadoPagoRefundResponse;
import com.jefiro.app247.domain.model.enum_type.order.OrderStatus;
import com.jefiro.app247.infra.exception.ExternalFailureType;
import com.jefiro.app247.infra.exception.ExternalServiceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PointPaymentRefundServiceTest {
    @Mock PaymentRefundPersistenceService persistence;
    @Mock OrderService orders;
    @Mock MercadoPagoCobrancaService mercadoPago;
    @Mock PaymentStateTransitionService transitions;
    @Mock PaymentReconciliationService reconciliation;
    PointPaymentRefundService service;
    Order processed;

    @BeforeEach
    void setUp() {
        service = new PointPaymentRefundService(persistence, orders, mercadoPago, transitions, reconciliation);
        processed = new Order(); processed.setIdOrder("order-a"); processed.setStatus(OrderStatus.PROCESSED);
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setIdPagamento("attempt-a"); attempt.setProviderOrderId("mp-order-a");
        attempt.setRefundRequestedAt(Instant.now()); processed.setPagamento(attempt);
    }

    @Test
    void enviaReembolsoEDevolveEstadoConfirmado() {
        Order refunded = processed;
        when(persistence.markRequested("order-a")).thenReturn(processed);
        when(orders.getOrderForReconciliation("order-a")).thenReturn(processed, refunded);
        var remote = new MercadoPagoRefundResponse("mp-order-a", "refunded", "refunded", null);
        when(mercadoPago.refundRemoteOrder(processed)).thenReturn(remote);
        doAnswer(call -> {
            processed.setStatus(OrderStatus.REFUNDED);
            processed.getPagamento().setRefundConfirmedAt(Instant.now());
            return true;
        }).when(transitions).apply(any());

        var result = service.refund("order-a");

        assertThat(result.confirmed()).isTrue();
        verify(mercadoPago).refundRemoteOrder(processed);
    }

    @Test
    void timeoutReconsultaEConfirmaSemNovaChave() {
        when(persistence.markRequested("order-a")).thenReturn(processed);
        when(orders.getOrderForReconciliation("order-a")).thenReturn(processed);
        when(mercadoPago.refundRemoteOrder(processed)).thenThrow(new ExternalServiceException(
                "Mercado Pago", ExternalFailureType.TIMEOUT, "timeout", null));
        when(reconciliation.reconcileRefund("order-a", "REFUND_REQUEST")).thenAnswer(call -> {
            processed.setStatus(OrderStatus.REFUNDED);
            processed.getPagamento().setRefundConfirmedAt(Instant.now());
            return true;
        });

        var result = service.refund("order-a");

        assertThat(result.confirmed()).isTrue();
        verify(reconciliation).reconcileRefund("order-a", "REFUND_REQUEST");
    }

    @Test
    void conflito409SemConfirmacaoDevolvePendenteEmVezDeErro() {
        when(persistence.markRequested("order-a")).thenReturn(processed);
        when(orders.getOrderForReconciliation("order-a")).thenReturn(processed);
        var http409 = HttpClientErrorException.create(HttpStatus.CONFLICT, "Conflict",
                HttpHeaders.EMPTY, new byte[0], null);
        when(mercadoPago.refundRemoteOrder(processed)).thenThrow(new ExternalServiceException(
                "Mercado Pago", ExternalFailureType.INVALID_REQUEST, "HTTP 409", http409));
        when(reconciliation.reconcileRefund("order-a", "REFUND_REQUEST")).thenReturn(false);

        var result = service.refund("order-a");

        assertThat(result.confirmed()).isFalse();
        assertThat(result.requestedAt()).isNotNull();
    }

    @Test
    void reconciliacaoQueFalhaEmTimeoutAindaDevolvePendente() {
        when(persistence.markRequested("order-a")).thenReturn(processed);
        when(orders.getOrderForReconciliation("order-a")).thenReturn(processed);
        when(mercadoPago.refundRemoteOrder(processed)).thenThrow(new ExternalServiceException(
                "Mercado Pago", ExternalFailureType.TIMEOUT, "timeout", null));
        when(reconciliation.reconcileRefund("order-a", "REFUND_REQUEST"))
                .thenThrow(new IllegalStateException("consulta falhou"));

        assertThat(service.refund("order-a").confirmed()).isFalse();
    }

    @Test
    void recusaDefinitivaContinuaComoErro() {
        when(persistence.markRequested("order-a")).thenReturn(processed);
        when(orders.getOrderForReconciliation("order-a")).thenReturn(processed);
        var http400 = HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request",
                HttpHeaders.EMPTY, new byte[0], null);
        var refusal = new ExternalServiceException(
                "Mercado Pago", ExternalFailureType.INVALID_REQUEST, "HTTP 400", http400);
        when(mercadoPago.refundRemoteOrder(processed)).thenThrow(refusal);
        when(reconciliation.reconcileRefund("order-a", "REFUND_REQUEST")).thenReturn(false);

        assertThatThrownBy(() -> service.refund("order-a")).isSameAs(refusal);
    }
}
