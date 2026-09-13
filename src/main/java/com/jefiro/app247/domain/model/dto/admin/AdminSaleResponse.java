package com.jefiro.app247.domain.model.dto.admin;

import com.jefiro.app247.domain.model.enum_type.order.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record AdminSaleResponse(
        String id,
        OrderStatus status,
        BigDecimal total,
        Instant criadaEm,
        Instant pagaEm,
        String condominioId,
        String condominioNome,
        String terminalId,
        String terminalNome
) {
}
