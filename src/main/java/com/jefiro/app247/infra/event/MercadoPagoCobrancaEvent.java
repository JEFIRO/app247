package com.jefiro.app247.infra.event;

import com.jefiro.app247.domain.model.Order;
import lombok.Getter;

@Getter
public class MercadoPagoCobrancaEvent {
    private final Order order;

    public MercadoPagoCobrancaEvent(Order order) {
        this.order = order;
    }
}
