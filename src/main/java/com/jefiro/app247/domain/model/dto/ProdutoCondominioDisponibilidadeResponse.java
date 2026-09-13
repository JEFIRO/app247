package com.jefiro.app247.domain.model.dto;

import java.math.BigDecimal;

public record ProdutoCondominioDisponibilidadeResponse(
        String condominioId,
        String condominioNome,
        boolean associado,
        BigDecimal quantidade
) {
}
