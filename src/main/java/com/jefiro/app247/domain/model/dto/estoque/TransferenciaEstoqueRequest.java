package com.jefiro.app247.domain.model.dto.estoque;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record TransferenciaEstoqueRequest(
        @NotBlank String condominioDestinoId,
        @Size(max=600) String observacao,
        @NotEmpty List<@Valid Item> itens
) {
    public record Item(
            @NotBlank String produtoId,
            @NotNull @DecimalMin(value="0.000", inclusive=false) BigDecimal quantidade
    ) {}
}
