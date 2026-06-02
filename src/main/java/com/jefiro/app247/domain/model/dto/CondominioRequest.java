package com.jefiro.app247.domain.model.dto;

public record CondominioRequest(
        String nome,
        String cnpj,
        EnderecoDTO endereco
) {
}
