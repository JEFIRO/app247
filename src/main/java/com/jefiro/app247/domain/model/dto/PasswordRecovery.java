package com.jefiro.app247.domain.model.dto;

public record PasswordRecovery(
        String code,
        String email,
        String cpf,
        String nome
) {
}
