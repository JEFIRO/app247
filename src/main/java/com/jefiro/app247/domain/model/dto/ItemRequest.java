package com.jefiro.app247.domain.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record ItemRequest(
        @NotBlank String productId,
        @NotNull @Positive Integer quantity,
        BigDecimal receivedWeight

) {
}
