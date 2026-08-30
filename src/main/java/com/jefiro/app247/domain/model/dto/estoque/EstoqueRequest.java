package com.jefiro.app247.domain.model.dto.estoque;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record EstoqueRequest(@NotBlank String produtoId, @NotNull BigDecimal quantidade, Boolean ativo) {}
