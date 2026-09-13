package com.jefiro.app247.domain.model.dto;

import com.jefiro.app247.domain.model.ProdutoFiscal;

import java.math.BigDecimal;

public record ProdutoFiscalDTO(
        String ncm,
        String cest,
        String origemMercadoria,
        String unidadeTributavel,
        BigDecimal fatorConversaoTributavel,
        String gtinTributavel,
        String perfilTributarioId
) {
    public ProdutoFiscalDTO(ProdutoFiscal fiscal) {
        this(fiscal.getNcm(), fiscal.getCest(), fiscal.getOrigemMercadoria(),
                fiscal.getUnidadeTributavel(), fiscal.getFatorConversaoTributavel(),
                fiscal.getGtinTributavel(), fiscal.getPerfilTributario() == null
                        ? null : fiscal.getPerfilTributario().getId());
    }
}
