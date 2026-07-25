package com.jefiro.app247.domain.model.dto.mercadopago;

public record OrderRequest(
        String type,
        String external_reference,
        String description,
        TransactionsRequest transactions,
        ConfigRequest config
) {
}