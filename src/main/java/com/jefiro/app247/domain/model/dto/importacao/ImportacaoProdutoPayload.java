package com.jefiro.app247.domain.model.dto.importacao;

import java.math.BigDecimal;
import java.util.List;

public record ImportacaoProdutoPayload(
        int linha,
        String codigoInterno,
        String nome,
        String descricao,
        BigDecimal precoVenda,
        String categoria,
        String unidadeMedida,
        BigDecimal peso,
        BigDecimal pesoTolerancia,
        boolean ativo,
        Fiscal fiscal,
        List<CodigoBarras> codigosBarras,
        EstoqueInicial estoqueInicial
) {
    public record Fiscal(String ncm, String cest, String origemMercadoria,
                         String gtinTributavel, String unidadeTributavel) {}
    public record CodigoBarras(String codigo, String tipo, boolean principal, boolean ativo) {}
    public record EstoqueInicial(BigDecimal quantidade, String motivo, boolean ativo) {}
}
