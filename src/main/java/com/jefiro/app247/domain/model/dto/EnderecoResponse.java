package com.jefiro.app247.domain.model.dto;

import com.jefiro.app247.domain.model.auth.Endereco;

public record EnderecoResponse(
        String rua,
        String numero,
        String complemento,
        String bairro,
        String cidade,
        String estado,
        String cep
) {
    public EnderecoResponse(Endereco endereco) {
        this(endereco.getRua(), endereco.getNumero(), endereco.getComplemento(), endereco.getBairro(), endereco.getCidade(), endereco.getEstado(), endereco.getCep());
    }
}
