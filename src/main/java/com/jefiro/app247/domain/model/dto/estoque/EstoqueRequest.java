package com.jefiro.app247.domain.model.dto.estoque;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record EstoqueRequest(@NotBlank String produtoId, @NotNull @DecimalMax(value = "999999999999", message = "Quantidade fora do limite permitido") BigDecimal quantidade, Boolean ativo) {}
