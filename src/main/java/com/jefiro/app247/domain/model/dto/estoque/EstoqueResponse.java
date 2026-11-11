package com.jefiro.app247.domain.model.dto.estoque;

import com.jefiro.app247.domain.model.EstoqueCondominio;
import java.math.BigDecimal;

public record EstoqueResponse(String produtoId, String produto, BigDecimal quantidade, boolean ativo,
                              BigDecimal estoqueMinimo, boolean abaixoDoMinimo) {
    public EstoqueResponse(String produtoId, String produto, BigDecimal quantidade, boolean ativo) {
        this(produtoId, produto, quantidade, ativo, null, false);
    }

    public EstoqueResponse(EstoqueCondominio estoque) {
        this(estoque.getProduto().getIdProduto(), estoque.getProduto().getNome(),
                estoque.getQuantidade(), estoque.getAtivo(),
                estoque.getProduto().getEstoqueMinimoCondominio(),
                estoque.getProduto().condominioAbaixoDoMinimo(estoque.getQuantidade()));
    }
}
