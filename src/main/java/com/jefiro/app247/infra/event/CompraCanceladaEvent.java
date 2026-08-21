package com.jefiro.app247.infra.event;

import com.jefiro.app247.domain.model.Order;
import lombok.Getter;

@Getter
public class CompraCanceladaEvent {

    private final Order order;

    public CompraCanceladaEvent(Order order) {
        this.order = order;
    }
}
