package com.jefiro.app247.domain.model.dto.estoque;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlanogramaRequest(
        @NotBlank @Size(max=150) String nome,
        @Size(max=600) String descricao,
        Boolean ativo
) {}
