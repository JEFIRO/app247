package com.jefiro.app247.domain.model.dto;

import com.jefiro.app247.infra.exception.PriceChangedException;

import java.math.BigDecimal;
import java.util.List;

public record PriceChangedResponse(
        String code,
        String message,
        List<PriceChangedException.ChangedItem> items,
        BigDecimal totalCalculado,
        BigDecimal totalCobrado,
        boolean requiresConfirmation
) {
    public static PriceChangedResponse from(PriceChangedException exception) {
        return new PriceChangedResponse(
                "PRICE_CHANGED", exception.getMessage(), exception.getItems(),
                exception.getTotalCalculado(), exception.getTotalCobrado(), true);
    }
}
