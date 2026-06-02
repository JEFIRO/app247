package com.jefiro.app247.domain.model.dto;

public record CondominioResponse(
        Long condominioId,
        String nome,
        EnderecoResponse endereco
) {
}
