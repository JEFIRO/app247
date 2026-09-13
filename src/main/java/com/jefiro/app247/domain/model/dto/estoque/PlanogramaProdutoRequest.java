package com.jefiro.app247.domain.model.dto.estoque;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record PlanogramaProdutoRequest(
        @NotBlank String produtoId,
        @Min(1) Integer facings,
        @DecimalMin("0") BigDecimal capacidade,
        @DecimalMin("0") BigDecimal quantidadeIdeal,
        @DecimalMin("0") BigDecimal quantidadeMinima,
        Boolean ativo
) {}
