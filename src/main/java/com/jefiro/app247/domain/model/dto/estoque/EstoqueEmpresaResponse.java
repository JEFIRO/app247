package com.jefiro.app247.domain.model.dto.estoque;

import com.jefiro.app247.domain.model.EstoqueEmpresa;
import com.jefiro.app247.domain.model.enum_type.ProdutoCategoria;

import java.math.BigDecimal;

public record EstoqueEmpresaResponse(
        String id,
        String produtoId,
        String produto,
        String codigoInterno,
        String codigoBarrasPrincipal,
        ProdutoCategoria categoria,
        String unidadeMedida,
        BigDecimal quantidade,
        boolean ativo,
        String localizacao,
        BigDecimal capacidadePosicao
) {
    public EstoqueEmpresaResponse(EstoqueEmpresa estoque, String localizacao, BigDecimal capacidadePosicao) {
        this(estoque.getId(), estoque.getProduto().getIdProduto(), estoque.getProduto().getNome(),
                estoque.getProduto().getCodigoInterno(), estoque.getProduto().getCodigo(),
                estoque.getProduto().getCategoria(), estoque.getProduto().getUnidadeMedida().name(),
                estoque.getQuantidade(), Boolean.TRUE.equals(estoque.getAtivo()), localizacao, capacidadePosicao);
    }
}
