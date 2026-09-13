package com.jefiro.app247.domain.model.dto;

import com.jefiro.app247.domain.model.Produto;
import com.jefiro.app247.domain.model.enum_type.ProdutoCategoria;
import com.jefiro.app247.domain.model.enum_type.UnidadeMedida;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ProdutoResponse(
        String id,
        String codigo,
        String codigoInterno,
        List<ProdutoCodigoBarrasDTO> codigosBarras,
        String nome,
        String descricao,
        BigDecimal preco,
        UnidadeMedida unidadeMedida,
        ProdutoCategoria categoria,
        BigDecimal peso,
        BigDecimal pesoTolerancia,
        String foto,
        boolean ativo,
        ProdutoFiscalDTO fiscal,
        Instant createdAt,
        Instant updatedAt
) {
    public ProdutoResponse(Produto produto) {
        this(produto.getIdProduto(), produto.getCodigo(), produto.getCodigoInterno(),
                produto.getCodigosBarras().stream().map(ProdutoCodigoBarrasDTO::new).toList(),
                produto.getNome(), produto.getDescricao(),
                produto.getPreco(), produto.getUnidadeMedida(), produto.getCategoria(), produto.getPeso(),
                produto.getPesoTolerancia(), produto.getFoto(), produto.isStatus(),
                produto.getFiscal() == null ? null : new ProdutoFiscalDTO(produto.getFiscal()), produto.getCreateAt(),
                produto.getUpdateAt());
    }
}
