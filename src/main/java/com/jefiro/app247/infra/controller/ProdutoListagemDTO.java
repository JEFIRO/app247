package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.Produto;
import com.jefiro.app247.domain.model.enum_type.ProdutoCategoria;
import com.jefiro.app247.domain.model.enum_type.UnidadeMedida;

import java.math.BigDecimal;

public record ProdutoListagemDTO(
        String id,
        String codigo,
        String nome,
        BigDecimal preco,
        @Deprecated Integer quantidade,
        UnidadeMedida unidadeMedida,
        ProdutoCategoria categoria,
        String foto,
        boolean status
) {
    public ProdutoListagemDTO(Produto produto) {
        this(
                produto.getIdProduto(),
                produto.getCodigo(),
                produto.getNome(),
                produto.getPreco(),
                null,
                produto.getUnidadeMedida(),
                produto.getCategoria(),
                produto.getFoto(),
                produto.isStatus()
        );
    }
}
