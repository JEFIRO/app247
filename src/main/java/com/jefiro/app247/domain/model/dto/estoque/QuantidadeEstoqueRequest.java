package com.jefiro.app247.domain.model.dto.estoque;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record QuantidadeEstoqueRequest(@NotNull BigDecimal quantidade, String motivo) {}
