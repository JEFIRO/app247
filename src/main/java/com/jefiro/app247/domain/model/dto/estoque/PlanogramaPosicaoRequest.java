package com.jefiro.app247.domain.model.dto.estoque;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PlanogramaPosicaoRequest(
        @Size(max=100) String setor,
        @Size(max=50) String corredor,
        @Size(max=50) String estante,
        @Size(max=50) String modulo,
        @Size(max=50) String prateleira,
        @Size(max=50) String posicao,
        Integer ordem,
        @DecimalMin("0") BigDecimal x,
        @DecimalMin("0") BigDecimal y,
        @DecimalMin("0") BigDecimal largura,
        @DecimalMin("0") BigDecimal altura
) {}
