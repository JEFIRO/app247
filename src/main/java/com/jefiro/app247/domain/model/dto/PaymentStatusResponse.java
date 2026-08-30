package com.jefiro.app247.domain.model.dto;

import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.Pagamento;
import com.jefiro.app247.domain.model.enum_type.TerminalPaymentStatus;
import com.jefiro.app247.domain.model.mapper.MercadoPagoStatusMapper;

import java.time.LocalDateTime;

public record PaymentStatusResponse(
        String type,
        String orderId,
        String paymentId,
        String terminalId,
        TerminalPaymentStatus status,
        String mercadoPagoStatus,
        String transactionId,
        String statusDetail,
        String message,
        LocalDateTime updatedAt,
        boolean reconciled
) {
    public static PaymentStatusResponse from(Order order, boolean reconciled) {
        Pagamento pagamento = order.getPagamento();
        TerminalPaymentStatus terminalStatus = MercadoPagoStatusMapper.toTerminalStatus(order.getStatus());
        return new PaymentStatusResponse(
                "PAYMENT_STATUS",
                order.getIdOrder(),
                pagamento != null ? pagamento.getIdPagamento() : null,
                order.getCarrinho() != null ? order.getCarrinho().getIdTerminal() : order.getIdTerminal(),
                terminalStatus,
                order.getStatus() != null ? order.getStatus().getValue() : null,
                pagamento != null ? pagamento.getTransactionId() : null,
                pagamento != null ? pagamento.getStatusDetail() : null,
                MercadoPagoStatusMapper.message(terminalStatus),
                pagamento != null && pagamento.getUpdatedAt() != null
                        ? pagamento.getUpdatedAt() : order.getUpdatedAt(),
                reconciled
        );
    }
}
