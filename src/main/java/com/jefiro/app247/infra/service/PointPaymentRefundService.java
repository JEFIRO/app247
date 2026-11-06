package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.dto.MercadoPagoRefundResponse;
import com.jefiro.app247.domain.model.dto.admin.AdminRefundResponse;
import com.jefiro.app247.domain.model.enum_type.order.OrderStatus;
import com.jefiro.app247.infra.exception.ExternalServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

@Service
public class PointPaymentRefundService {
    private static final Logger log = LoggerFactory.getLogger(PointPaymentRefundService.class);
    private final PaymentRefundPersistenceService persistence;
    private final OrderService orders;
    private final MercadoPagoCobrancaService mercadoPago;
    private final PaymentStateTransitionService transitions;
    private final PaymentReconciliationService reconciliation;

    public PointPaymentRefundService(PaymentRefundPersistenceService persistence,
                                     OrderService orders,
                                     MercadoPagoCobrancaService mercadoPago,
                                     PaymentStateTransitionService transitions,
                                     PaymentReconciliationService reconciliation) {
        this.persistence = persistence;
        this.orders = orders;
        this.mercadoPago = mercadoPago;
        this.transitions = transitions;
        this.reconciliation = reconciliation;
    }

    public AdminRefundResponse refund(String orderId) {
        Order requested = persistence.markRequested(orderId);
        if (requested.getStatus() == OrderStatus.REFUNDED) {
            return AdminRefundResponse.from(requested);
        }
        Order order = orders.getOrderForReconciliation(orderId);
        log.warn("[PAYMENT-REFUND] reembolso integral solicitado orderId={} attemptId={} remoteOrderId={}",
                orderId, order.getPagamento().getIdPagamento(), order.getMpOrderId());
        try {
            MercadoPagoRefundResponse remote = mercadoPago.refundRemoteOrder(order);
            transitions.apply(MercadoPagoOrderState.fromRefund(remote, order.getPagamento()));
        } catch (ExternalServiceException failure) {
            log.warn("[PAYMENT-REFUND] resposta não confirmada; reconciliando orderId={} failureType={}",
                    orderId, failure.getFailureType());
            if (!reconcileQuietly(orderId) && !isUncertain(failure)) throw failure;
            // Incerto: a intenção já está persistida; webhook e agendador confirmam depois.
        }
        return AdminRefundResponse.from(orders.getOrderForReconciliation(orderId));
    }

    private boolean reconcileQuietly(String orderId) {
        try {
            return reconciliation.reconcileRefund(orderId, "REFUND_REQUEST");
        } catch (RuntimeException error) {
            log.warn("[PAYMENT-REFUND] reconciliação imediata falhou orderId={} errorType={}",
                    orderId, error.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * Falhas em que o Mercado Pago pode ter aceitado o reembolso. HTTP 409 aparece quando o
     * reembolso já está em processamento; recusas definitivas (401, 403, 400, 422) continuam erro.
     */
    static boolean isUncertain(ExternalServiceException failure) {
        if (failure.getCause() instanceof RestClientResponseException response
                && response.getStatusCode().value() == 409) {
            return true;
        }
        return switch (failure.getFailureType()) {
            case TIMEOUT, UNAVAILABLE, UNKNOWN, IDEMPOTENCY_CONFLICT -> true;
            default -> false;
        };
    }
}
