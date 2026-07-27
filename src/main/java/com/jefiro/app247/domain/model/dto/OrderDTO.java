package com.jefiro.app247.domain.model.dto;

import com.jefiro.app247.domain.model.enum_type.order.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderDTO(
        String orderId,
        OrderStatus status,
        BigDecimal subtotal,
        BigDecimal desconto,
        BigDecimal total,
        LocalDateTime createdAt
) {
}
