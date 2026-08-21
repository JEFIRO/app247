package com.jefiro.app247.infra.event;

import com.jefiro.app247.domain.model.dto.PointPaymentResponse;
import com.jefiro.app247.domain.model.enum_type.TerminalPaymentStatus;
import com.jefiro.app247.domain.model.enum_type.order.OrderStatus;
import lombok.Data;

@Data
public class PaymentEvent {
    private String type;
    private String terminalId;
    private String orderId;
    private String transactionId;
    private TerminalPaymentStatus status;
    private OrderStatus mercadoPagoStatus;
    private String statusDetail;
    private String message;
    /** Compatibilidade temporária com consumidores do contrato antigo. */
    private String paid;

    public PaymentEvent(String terminalId, String orderId, String transactionId, String paid) {
        this.type = "PAYMENT_STATUS";
        this.terminalId = terminalId;
        this.orderId = orderId;
        this.transactionId = transactionId;
        this.paid = paid;
    }

    public PaymentEvent(PointPaymentResponse response) {
        this.type = response.type();
        this.terminalId = response.terminalId();
        this.orderId = response.orderId();
        this.transactionId = response.transactionId();
        this.status = response.status();
        this.mercadoPagoStatus = response.mercadoPagoStatus();
        this.statusDetail = response.statusDetail();
        this.message = response.message();
        this.paid = response.status() == TerminalPaymentStatus.APPROVED ? "PAID" : response.status().name();
    }
}
