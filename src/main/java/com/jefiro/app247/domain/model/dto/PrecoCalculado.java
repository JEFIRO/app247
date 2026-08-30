package com.jefiro.app247.domain.model.dto;

import com.jefiro.app247.domain.model.Produto;
import com.jefiro.app247.domain.model.Promocao;

import java.math.BigDecimal;

public record PrecoCalculado(
        Produto produto,
        BigDecimal precoOriginal,
        BigDecimal precoCalculado,
        BigDecimal descontoCalculado,
        Promocao promocao
) {
    public boolean emPromocao() {
        return promocao != null;
    }

    public BigDecimal subtotal(int quantidade) {
        return precoCalculado.multiply(BigDecimal.valueOf(quantidade));
    }
}
