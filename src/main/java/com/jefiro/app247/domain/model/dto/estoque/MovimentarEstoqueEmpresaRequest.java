package com.jefiro.app247.domain.model.dto.estoque;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record MovimentarEstoqueEmpresaRequest(
        @NotNull BigDecimal quantidade,
        @Size(max=300) String motivo
) {}
