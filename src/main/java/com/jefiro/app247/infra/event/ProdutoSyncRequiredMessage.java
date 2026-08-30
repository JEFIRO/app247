package com.jefiro.app247.infra.event;

import com.jefiro.app247.domain.model.enum_type.ProdutoCatalogChangeReason;

public record ProdutoSyncRequiredMessage(
        String type,
        ProdutoCatalogChangeReason reason,
        String productId
) {
    public ProdutoSyncRequiredMessage(ProdutoCatalogChangedEvent event) {
        this("PRODUCT_SYNC_REQUIRED", event.reason(), event.productId());
    }
}
