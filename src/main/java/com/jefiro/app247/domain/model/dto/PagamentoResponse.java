package com.jefiro.app247.domain.model.dto;


import com.jefiro.app247.domain.model.enum_type.PagamentoStatus;
import com.jefiro.app247.domain.model.enum_type.PagamentoTipo;

import java.math.BigDecimal;

public record PagamentoResponse(

        String pagamentoId,

        String orderId,

        BigDecimal valor,

        PagamentoTipo tipo,

        PagamentoStatus status,

        String transactionId,

        String qrCode,

        String qrCodeBase64

) {
}