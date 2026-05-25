package com.jefiro.app247.domain.model.dto;

import com.jefiro.app247.domain.model.Carrinho;
import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.enum_type.OrderStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderResponse(

        String orderId,
        String carrinho_id,
        BigDecimal total,
        OrderStatus status,
        LocalDateTime createdAt
) {
    public OrderResponse(Order order) {
        this(order.getOrderId(), order.getCarrinho().getCarrinhoId(), order.getTotal(), order.getStatus(), order.getCreatedAt());
    }
}
