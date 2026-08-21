package com.jefiro.app247.domain.model.dto;

import com.jefiro.app247.domain.model.Produto;
import com.jefiro.app247.domain.model.enum_type.ProdutoCategoria;
import com.jefiro.app247.domain.model.enum_type.UnidadeMedida;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProdutoResponse(
        String id,
        String codigo,
        String nome,
        String descricao,
        BigDecimal preco,
        UnidadeMedida unidadeMedida,
        ProdutoCategoria categoria,
        BigDecimal peso,
        BigDecimal pesoTolerancia,
        String foto,
        boolean ativo,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public ProdutoResponse(Produto produto) {
        this(produto.getIdProduto(), produto.getCodigo(), produto.getNome(), produto.getDescricao(),
                produto.getPreco(), produto.getUnidadeMedida(), produto.getCategoria(), produto.getPeso(),
                produto.getPesoTolerancia(), produto.getFoto(), produto.isStatus(), produto.getCreateAt(),
                produto.getUpdateAt());
    }
}
