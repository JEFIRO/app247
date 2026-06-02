package com.jefiro.app247.domain.model.dto;

public record EnderecoDTO(
        String rua,
        String numero,
        String complemento,
        String bairro,
        String cidade,
        String estado,
        String cep
) {
}
