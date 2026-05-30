package com.jefiro.app247.domain.model.dto;

public record ResetPasswordRequest(
        String cpf,
        String token,
        String novaSenha
) {
}
