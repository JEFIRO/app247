package com.jefiro.app247.infra.event;

import com.jefiro.app247.domain.model.Order;
import lombok.Getter;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Getter
public class MercadoPagoCobrancaEvent {
    private final Order order;


    public MercadoPagoCobrancaEvent(Order order) {
        this.order = order;
    }
}
