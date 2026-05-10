package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.Produto;
import com.jefiro.app247.domain.model.enum_type.ProdutoCategoria;
import com.jefiro.app247.domain.model.enum_type.UnidadeMedida;

import java.math.BigDecimal;

public record ProdutoListagemDTO(
        Long id,
        String codigo,
        String nome,
        BigDecimal preco,
        Integer quantidade,
        UnidadeMedida unidadeMedida,
        ProdutoCategoria categoria,
        String foto,
        boolean status
) {
    public ProdutoListagemDTO(Produto produto) {
        this(
                produto.getId(),
                produto.getCodigo(),
                produto.getNome(),
                produto.getPreco(),
                produto.getQuantidade(),
                produto.getUnidadeMedida(),
                produto.getCategoria(),
                produto.getFoto(),
                produto.isStatus()
        );
    }
}
