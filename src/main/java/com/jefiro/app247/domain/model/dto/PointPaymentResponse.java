package com.jefiro.app247.domain.model.dto;

import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.PaymentAttempt;
import com.jefiro.app247.domain.model.enum_type.TerminalPaymentStatus;
import com.jefiro.app247.domain.model.enum_type.order.OrderStatus;
import com.jefiro.app247.domain.model.mapper.MercadoPagoStatusMapper;

public record PointPaymentResponse(
        String type,
        String orderId,
        String paymentAttemptId,
        String terminalId,
        TerminalPaymentStatus status,
        OrderStatus mercadoPagoStatus,
        String transactionId,
        String statusDetail,
        String message
) {
    public PointPaymentResponse(String type, String orderId, String terminalId,
                                TerminalPaymentStatus status, OrderStatus mercadoPagoStatus,
                                String transactionId, String statusDetail, String message) {
        this(type, orderId, null, terminalId, status, mercadoPagoStatus,
                transactionId, statusDetail, message);
    }

    public static PointPaymentResponse from(Order order) {
        PaymentAttempt pagamento = order.getPagamento();
        TerminalPaymentStatus terminalStatus = MercadoPagoStatusMapper.toTerminalStatus(order.getStatus());
        return new PointPaymentResponse(
                "PAYMENT_STATUS",
                order.getIdOrder(),
                pagamento != null ? pagamento.getIdPagamento() : null,
                order.getCarrinho() != null ? order.getCarrinho().getIdTerminal() : order.getIdTerminal(),
                terminalStatus,
                order.getStatus(),
                pagamento != null ? pagamento.getTransactionId() : null,
                pagamento != null ? pagamento.getStatusDetail() : null,
                MercadoPagoStatusMapper.message(terminalStatus)
        );
    }
}
