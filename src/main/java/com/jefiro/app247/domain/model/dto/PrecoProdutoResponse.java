package com.jefiro.app247.domain.model.dto;

import com.jefiro.app247.domain.model.dto.PrecoCalculado;
import com.jefiro.app247.domain.model.enum_type.AbrangenciaPromocao;
import com.jefiro.app247.domain.model.enum_type.TipoPromocao;

import java.math.BigDecimal;

public record PrecoProdutoResponse(
        String produtoId,
        BigDecimal precoOriginal,
        BigDecimal precoCalculado,
        boolean emPromocao,
        PromocaoResumo promocao
) {
    public static PrecoProdutoResponse from(PrecoCalculado preco) {
        var promocao = preco.promocao();
        return new PrecoProdutoResponse(
                preco.produto().getIdProduto(), preco.precoOriginal(), preco.precoCalculado(),
                preco.emPromocao(), promocao == null ? null : new PromocaoResumo(
                promocao.getIdPromocao(), promocao.getNome(), promocao.getTipo(),
                promocao.getValor(), promocao.getAbrangencia()));
    }

    public record PromocaoResumo(
            String id,
            String nome,
            TipoPromocao tipo,
            BigDecimal valor,
            AbrangenciaPromocao abrangencia
    ) {
    }
}
