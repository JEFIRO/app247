package com.jefiro.app247.domain.model.dto.notafiscal;

import java.math.BigDecimal;

/** Ajuste de um item na revisão: vincular a um produto existente ou criar um novo. */
public record NotaFiscalItemRequest(
        String acao,
        String produtoId,
        String nomeProduto,
        BigDecimal precoVenda,
        String categoria,
        BigDecimal fatorConversao
) {}
