package com.jefiro.app247.domain.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record ItemRequest(
        @NotBlank String productId,
        @NotNull @Positive BigDecimal quantity,
        BigDecimal receivedWeight,
        BigDecimal expectedUnitPrice,
        String codigoBarras

) {
    public ItemRequest(String productId, Integer quantity, BigDecimal receivedWeight) {
        this(productId, new BigDecimal(quantity), receivedWeight, null, null);
    }

    public ItemRequest(String productId, Integer quantity, BigDecimal receivedWeight, BigDecimal expectedUnitPrice) {
        this(productId, new BigDecimal(quantity), receivedWeight, expectedUnitPrice, null);
    }

    public ItemRequest(String productId, BigDecimal quantity, BigDecimal receivedWeight, BigDecimal expectedUnitPrice) {
        this(productId, quantity, receivedWeight, expectedUnitPrice, null);
    }
}
