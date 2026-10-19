package com.jefiro.app247.domain.model.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthDTO(
        @NotBlank
        @Size(max = 20)
        String cpf,
        // Redefinição de senha aceita até 100; o login não pode ser mais restrito que isso.
        @NotBlank
        @Size(max = 100)
        String senha
) {
}
