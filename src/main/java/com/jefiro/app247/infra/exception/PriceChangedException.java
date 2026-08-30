package com.jefiro.app247.infra.exception;

import java.math.BigDecimal;
import java.util.List;

public class PriceChangedException extends RuntimeException {
    private final List<ChangedItem> items;
    private final BigDecimal totalCalculado;
    private final BigDecimal totalCobrado;

    public PriceChangedException(List<ChangedItem> items, BigDecimal totalCalculado, BigDecimal totalCobrado) {
        super("O preço da compra foi atualizado. Revise os valores e confirme novamente.");
        this.items = List.copyOf(items);
        this.totalCalculado = totalCalculado;
        this.totalCobrado = totalCobrado;
    }

    public List<ChangedItem> getItems() { return items; }
    public BigDecimal getTotalCalculado() { return totalCalculado; }
    public BigDecimal getTotalCobrado() { return totalCobrado; }

    public record ChangedItem(
            String produtoId,
            String nome,
            BigDecimal precoExibido,
            BigDecimal precoOriginal,
            BigDecimal precoAtual,
            boolean emPromocao,
            String promocaoId,
            String promocaoNome,
            int quantidade,
            boolean aumentou
    ) {
    }
}
