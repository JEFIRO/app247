package com.jefiro.app247.domain.model.dto;

import com.jefiro.app247.domain.model.EstoqueCondominio;
import com.jefiro.app247.domain.model.enum_type.ProdutoSyncOperation;

public record ProdutoSyncChange(
        String productId,
        ProdutoSyncOperation operation,
        ProdutoSyncItem produto
) {
    public static ProdutoSyncChange from(EstoqueCondominio estoque, PrecoCalculado preco) {
        boolean disponivel = Boolean.TRUE.equals(estoque.getAtivo()) && estoque.getProduto().isStatus();
        return disponivel
                ? new ProdutoSyncChange(estoque.getProduto().getIdProduto(), ProdutoSyncOperation.UPSERT,
                        new ProdutoSyncItem(estoque, preco))
                : new ProdutoSyncChange(estoque.getProduto().getIdProduto(), ProdutoSyncOperation.REMOVE, null);
    }
}
