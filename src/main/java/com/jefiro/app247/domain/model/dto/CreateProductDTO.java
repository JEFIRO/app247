package com.jefiro.app247.domain.model.dto;

import jakarta.validation.constraints.*;

public record CreateProductDTO(

        @NotBlank(message = "O código do produto é obrigatório.")
        String codigo,

        @NotBlank(message = "O nome do produto não pode ficar vazio.")
        String nome,

        @NotNull(message = "O preço deve ser informado.")
        @Positive(message = "O preço deve ser maior que zero.")
        Double preco,

        @NotNull(message = "A quantidade em estoque é obrigatória.")
        @Min(value = 0, message = "A quantidade não pode ser negativa.")
        Integer quantidade,

        @NotBlank(message = "Informe a unidade de medida (ex: kg, g, un).")
        String unidadeMedida,

        @NotBlank(message = "A categoria do produto deve ser informada.")
        String categoria,

        String descricao,

        String foto,

        @NotNull(message = "O peso do produto é obrigatório.")
        @Positive(message = "O peso deve ser maior que zero.")
        Double peso,

        @NotNull(message = "A tolerância de peso deve ser informada.")
        @PositiveOrZero(message = "A tolerância de peso não pode ser negativa.")
        Double pesoTolerancia

) {}