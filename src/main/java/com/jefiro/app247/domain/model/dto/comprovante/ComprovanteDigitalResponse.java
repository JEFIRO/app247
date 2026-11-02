package com.jefiro.app247.domain.model.dto.comprovante;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Dados estruturados do comprovante de uma compra aprovada. A conta sempre fecha:
 * {@code subtotal - descontoTotal + arredondamento = total}.
 */
public record ComprovanteDigitalResponse(
        Estabelecimento estabelecimento,
        String numeroCompra,
        Instant pagoEm,
        String data,
        String hora,
        TerminalInfo terminal,
        List<Item> itens,
        BigDecimal subtotal,
        List<Desconto> descontos,
        BigDecimal descontoTotal,
        BigDecimal arredondamento,
        BigDecimal total,
        Pagamento pagamento,
        Cliente cliente,
        Fidelidade fidelidade,
        String qrCodeUrl,
        String aviso
) {
    public record Estabelecimento(
            String nome,
            String razaoSocial,
            String cnpj,
            String endereco,
            String local,
            String telefone,
            String email,
            String logoUrl,
            String logoDarkUrl
    ) {
    }

    public record TerminalInfo(String codigo, String nome) {
    }

    /** {@code subtotal} usa o preço original; {@code valorFinal} já desconta a promoção do item. */
    public record Item(
            String codigo,
            String descricao,
            BigDecimal quantidade,
            String unidade,
            BigDecimal valorUnitario,
            BigDecimal subtotal,
            BigDecimal desconto,
            BigDecimal valorFinal
    ) {
    }

    public record Desconto(String tipo, String descricao, BigDecimal valor) {
    }

    public record Pagamento(
            String forma,
            String bandeira,
            Integer parcelas,
            String status,
            String transacaoId,
            String nsu,
            String autorizacao
    ) {
    }

    public record Cliente(boolean vinculado, String primeiroNome) {
    }

    /** Benefício creditado por esta compra; ausente quando ela não gerou movimento. */
    public record Fidelidade(Long pontosGanhos, BigDecimal cashbackGanho) {
    }
}
