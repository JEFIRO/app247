package com.jefiro.app247.domain.model.dto.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Detalhe de uma venda para o painel administrativo. */
public record AdminSaleDetailResponse(
        String id,
        String status,
        Instant criadaEm,
        Instant pagaEm,
        String condominioNome,
        String terminalNome,
        String terminalCodigo,
        List<Item> itens,
        BigDecimal subtotal,
        BigDecimal descontoPromocoes,
        BigDecimal descontoCupom,
        BigDecimal total,
        Pagamento pagamento,
        Reembolso reembolso,
        boolean podeReembolsar,
        boolean podeEnviarComprovante
) {
    public record Item(
            String codigo,
            String descricao,
            BigDecimal quantidade,
            String unidade,
            BigDecimal valorUnitario,
            BigDecimal desconto,
            BigDecimal valorFinal
    ) {
    }

    public record Pagamento(
            String status,
            String forma,
            String bandeira,
            Integer parcelas,
            String transacaoId,
            String nsu,
            String autorizacao
    ) {
    }

    public record Reembolso(Instant solicitadoEm, Instant confirmadoEm) {
    }
}
