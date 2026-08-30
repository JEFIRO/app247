package com.jefiro.app247.domain.model.dto;

import com.jefiro.app247.domain.model.EstoqueCondominio;
import com.jefiro.app247.domain.model.enum_type.ProdutoCategoria;
import com.jefiro.app247.domain.model.enum_type.UnidadeMedida;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProdutoTerminalResponse(
        String id, String codigo, String nome, String descricao, BigDecimal precoOriginal,
        BigDecimal preco, boolean emPromocao, String promocaoId, String promocaoNome,
        UnidadeMedida unidadeMedida, ProdutoCategoria categoria, BigDecimal peso,
        BigDecimal pesoTolerancia, String foto, boolean ativo, BigDecimal quantidade,
        LocalDateTime createdAt, LocalDateTime updatedAt
) {
    public ProdutoTerminalResponse(EstoqueCondominio estoque, PrecoCalculado calculado) {
        this(estoque.getProduto().getIdProduto(), estoque.getProduto().getCodigo(),
                estoque.getProduto().getNome(), estoque.getProduto().getDescricao(),
                calculado.precoOriginal(), calculado.precoCalculado(), calculado.emPromocao(),
                calculado.promocao() != null ? calculado.promocao().getIdPromocao() : null,
                calculado.promocao() != null ? calculado.promocao().getNome() : null,
                estoque.getProduto().getUnidadeMedida(),
                estoque.getProduto().getCategoria(), estoque.getProduto().getPeso(),
                estoque.getProduto().getPesoTolerancia(), estoque.getProduto().getFoto(),
                Boolean.TRUE.equals(estoque.getAtivo()) && estoque.getProduto().isStatus(),
                estoque.getQuantidade(), estoque.getProduto().getCreateAt(),
                estoque.getProduto().getUpdateAt());
    }
}
