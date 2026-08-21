package com.jefiro.app247.domain.model.dto.estoque;

import com.jefiro.app247.domain.model.EstoqueCondominio;
import java.math.BigDecimal;

public record EstoqueResponse(String produtoId, String produto, BigDecimal quantidade, boolean ativo) {
    public EstoqueResponse(EstoqueCondominio estoque) {
        this(estoque.getProduto().getIdProduto(), estoque.getProduto().getNome(),
                estoque.getQuantidade(), estoque.getAtivo());
    }
}
