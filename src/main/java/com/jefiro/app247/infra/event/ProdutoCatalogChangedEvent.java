package com.jefiro.app247.infra.event;

import com.jefiro.app247.domain.model.enum_type.ProdutoCatalogChangeReason;

import java.util.Set;

public record ProdutoCatalogChangedEvent(
        String productId,
        ProdutoCatalogChangeReason reason,
        Set<String> condominiumIds
) {
    public ProdutoCatalogChangedEvent {
        condominiumIds = Set.copyOf(condominiumIds);
    }
}
