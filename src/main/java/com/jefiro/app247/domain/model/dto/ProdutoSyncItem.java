package com.jefiro.app247.domain.model.dto;

import com.jefiro.app247.domain.model.EstoqueCondominio;
import com.jefiro.app247.domain.model.enum_type.ProdutoCategoria;
import com.jefiro.app247.domain.model.enum_type.UnidadeMedida;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ProdutoSyncItem(
        String id,
        String codigo,
        String codigoInterno,
        List<ProdutoCodigoBarrasDTO> codigosBarras,
        String nome,
        String descricao,
        BigDecimal precoOriginal,
        BigDecimal preco,
        boolean emPromocao,
        String promocaoId,
        String promocaoNome,
        UnidadeMedida unidadeMedida,
        ProdutoCategoria categoria,
        BigDecimal peso,
        BigDecimal pesoTolerancia,
        String foto,
        boolean ativo,
        BigDecimal quantidade,
        Instant createdAt,
        Instant updatedAt
) {
    public ProdutoSyncItem(EstoqueCondominio estoque, PrecoCalculado precoCalculado) {
        this(
                estoque.getProduto().getIdProduto(),
                estoque.getProduto().getCodigo(),
                estoque.getProduto().getCodigoInterno(),
                estoque.getProduto().getCodigosBarras().stream()
                        .filter(c -> Boolean.TRUE.equals(c.getAtivo()))
                        .map(ProdutoCodigoBarrasDTO::new).toList(),
                estoque.getProduto().getNome(),
                estoque.getProduto().getDescricao(),
                precoCalculado.precoOriginal(),
                precoCalculado.precoCalculado(),
                precoCalculado.emPromocao(),
                precoCalculado.promocao() != null ? precoCalculado.promocao().getIdPromocao() : null,
                precoCalculado.promocao() != null ? precoCalculado.promocao().getNome() : null,
                estoque.getProduto().getUnidadeMedida(),
                estoque.getProduto().getCategoria(),
                estoque.getProduto().getPeso(),
                estoque.getProduto().getPesoTolerancia(),
                estoque.getProduto().getFoto(),
                Boolean.TRUE.equals(estoque.getAtivo()) && estoque.getProduto().isStatus(),
                estoque.getQuantidade(),
                estoque.getProduto().getCreateAt(),
                maisRecente(estoque)
        );
    }

    private static Instant maisRecente(EstoqueCondominio estoque) {
        var produtoUpdatedAt = estoque.getProduto().getUpdateAt();
        var estoqueUpdatedAt = estoque.getUpdatedAt();
        return produtoUpdatedAt.isAfter(estoqueUpdatedAt) ? produtoUpdatedAt : estoqueUpdatedAt;
    }
}
