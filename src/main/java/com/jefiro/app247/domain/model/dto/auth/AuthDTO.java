package com.jefiro.app247.domain.model.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record AuthDTO(
        @NotBlank
        String cpf,
        @NotBlank
        String senha
) {
}
