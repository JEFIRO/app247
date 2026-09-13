package com.jefiro.app247.domain.model.dto;

import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.PaymentAttempt;
import com.jefiro.app247.domain.model.enum_type.TerminalPaymentStatus;
import com.jefiro.app247.domain.model.mapper.MercadoPagoStatusMapper;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentStatusResponse(
        String type,
        String orderId,
        String cartId,
        String paymentId,
        String paymentAttemptId,
        String terminalId,
        TerminalPaymentStatus status,
        String mercadoPagoStatus,
        String transactionId,
        String statusDetail,
        String message,
        BigDecimal amount,
        Instant updatedAt,
        boolean reconciled
) {
    public static PaymentStatusResponse from(Order order, boolean reconciled) {
        PaymentAttempt pagamento = order.getPagamento();
        TerminalPaymentStatus terminalStatus = MercadoPagoStatusMapper.toTerminalStatus(order.getStatus());
        return new PaymentStatusResponse(
                "PAYMENT_STATUS",
                order.getIdOrder(),
                order.getCarrinho() != null ? order.getCarrinho().getIdCarrinho() : null,
                pagamento != null ? pagamento.getIdPagamento() : null,
                pagamento != null ? pagamento.getIdPagamento() : null,
                order.getCarrinho() != null ? order.getCarrinho().getIdTerminal() : order.getIdTerminal(),
                terminalStatus,
                order.getStatus() != null ? order.getStatus().getValue() : null,
                pagamento != null ? pagamento.getTransactionId() : null,
                pagamento != null ? pagamento.getStatusDetail() : null,
                MercadoPagoStatusMapper.message(terminalStatus),
                order.getTotalCobrado() != null ? order.getTotalCobrado() : order.getTotal(),
                pagamento != null && pagamento.getUpdatedAt() != null
                        ? pagamento.getUpdatedAt() : order.getUpdatedAt(),
                reconciled
        );
    }
}
