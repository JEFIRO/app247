package com.jefiro.app247.domain.model.dto;

import com.jefiro.app247.domain.model.Empresa;

public record EmpresaResponse(
        String id,
        String razaoSocial,
        String nomeFantasia,
        String cnpj,
        String email,
        String telefone,
        String cep,
        String logradouro,
        String numero,
        String bairro,
        String cidade,
        String estado,
        Boolean ativo
) {
    public EmpresaResponse(Empresa empresa) {
        this(empresa.getId(), empresa.getRazaoSocial(), empresa.getNomeFantasia(), empresa.getCnpj(),
                empresa.getEmail(), empresa.getTelefone(), empresa.getCep(), empresa.getLogradouro(),
                empresa.getNumero(), empresa.getBairro(), empresa.getCidade(), empresa.getEstado(),
                empresa.getAtivo());
    }
}
