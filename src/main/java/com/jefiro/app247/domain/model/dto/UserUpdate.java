package com.jefiro.app247.domain.model.dto;

public record UserUpdate(
        String nome,
        String sobrenome,
        String email,
        String telefone,
        Boolean ativo,
        String condominioId
) {
}
