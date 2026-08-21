package com.jefiro.app247.domain.model.dto;

public record CondominioResponse(
        String idCondominio,
        String nome,
        String cnpj,
        Boolean ativo,
        String empresaId,
        EnderecoResponse endereco
) {
    public CondominioResponse(com.jefiro.app247.domain.model.Condominio condominio) {
        this(condominio.getIdCondominio(), condominio.getNome(), condominio.getCnpj(),
                condominio.getAtivo(), condominio.getEmpresa().getId(),
                condominio.getEndereco() == null ? null : new EnderecoResponse(condominio.getEndereco()));
    }
}
