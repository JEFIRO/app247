package com.jefiro.app247.domain.model.dto;

import com.jefiro.app247.domain.model.ProdutoCodigoBarras;

public record ProdutoCodigoBarrasDTO(
        String id,
        String codigo,
        String tipo,
        boolean principal,
        boolean ativo
) {
    public ProdutoCodigoBarrasDTO(ProdutoCodigoBarras codigo) {
        this(codigo.getId(), codigo.getCodigoBarras(), codigo.getTipo(),
                Boolean.TRUE.equals(codigo.getPrincipal()), Boolean.TRUE.equals(codigo.getAtivo()));
    }
}
