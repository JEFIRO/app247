package com.jefiro.app247.domain.model.dto.estoque;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ContagemInventarioRequest(
        @NotNull @PositiveOrZero(message = "Quantidade contada não pode ser negativa") @DecimalMax(value = "999999999999", message = "Quantidade fora do limite permitido") BigDecimal quantidadeContada,
        @Size(max = 300) String motivoAjuste
) {}
