package com.jefiro.app247.domain.model.dto.admin;

import java.math.BigDecimal;
import java.time.Instant;

public record AdminSalesSummaryResponse(
        Instant from,
        Instant to,
        long quantidadeVendas,
        BigDecimal faturamento,
        BigDecimal ticketMedio
) {
}
