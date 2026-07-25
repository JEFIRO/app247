package com.jefiro.app247.domain.model.dto;

public record CondominioResponse(
        String idCondominio,
        String nome,
        EnderecoResponse endereco
) {
}
