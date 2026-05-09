package com.jefiro.app247.domain.model.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequestDTO(
        @NotBlank(message = "Email obrigatorio")
        String email,
        @NotBlank(message = "sennha não pode ser vazia")
        String senha

) {
}
