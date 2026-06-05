package com.jefiro.app247.infra.event;

import lombok.Data;

@Data
public class PaymentEvent {
    private String terminalId;
    private String orderId;
    private String transactionId;
    private String paid;

    public PaymentEvent(String terminalId, String orderId, String transactionId, String paid) {
        this.terminalId = terminalId;
        this.orderId = orderId;
        this.transactionId = transactionId;
        this.paid = paid;
    }
}
