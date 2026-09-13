package com.jefiro.app247.domain.model.dto.admin;

import com.jefiro.app247.domain.model.enum_type.PagamentoStatus;
import com.jefiro.app247.domain.model.enum_type.PaymentProvider;

import java.math.BigDecimal;
import java.time.Instant;

public record AdminPaymentResponse(
        String id,
        String orderId,
        int tentativa,
        PaymentProvider provider,
        PagamentoStatus status,
        String statusDetail,
        BigDecimal valor,
        Instant criadaEm,
        Instant atualizadaEm,
        String terminalId,
        String terminalNome
) {
}
