package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.Produto;
import com.jefiro.app247.domain.model.enum_type.ProdutoCategoria;
import com.jefiro.app247.domain.model.enum_type.UnidadeMedida;

import java.math.BigDecimal;
import java.util.List;
import com.jefiro.app247.domain.model.dto.ProdutoCodigoBarrasDTO;

public record ProdutoListagemDTO(
        String id,
        String codigo,
        String codigoInterno,
        String codigoBarrasPrincipal,
        int quantidadeCodigosBarras,
        List<ProdutoCodigoBarrasDTO> codigosBarras,
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
                produto.getIdProduto(), produto.getCodigo(), produto.getCodigoInterno(),
                produto.getCodigosBarras().stream()
                        .filter(c -> Boolean.TRUE.equals(c.getAtivo()) && Boolean.TRUE.equals(c.getPrincipal()))
                        .map(com.jefiro.app247.domain.model.ProdutoCodigoBarras::getCodigoBarras)
                        .findFirst().orElse(null),
                (int) produto.getCodigosBarras().stream().filter(c -> Boolean.TRUE.equals(c.getAtivo())).count(),
                produto.getCodigosBarras().stream().map(ProdutoCodigoBarrasDTO::new).toList(),
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
