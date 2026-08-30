package com.jefiro.app247.domain.model.dto;

import com.jefiro.app247.domain.model.Promocao;
import com.jefiro.app247.domain.model.enum_type.AbrangenciaPromocao;
import com.jefiro.app247.domain.model.enum_type.StatusPromocao;
import com.jefiro.app247.domain.model.enum_type.TipoPromocao;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

public record PromocaoResponse(
        String id,
        String nome,
        String descricao,
        AbrangenciaPromocao abrangencia,
        String condominioId,
        String condominioNome,
        TipoPromocao tipo,
        BigDecimal valor,
        Instant inicio,
        Instant fim,
        boolean ativo,
        StatusPromocao status,
        int prioridade,
        List<ProdutoResumo> produtos,
        Instant createdAt,
        Instant updatedAt
) {
    public static PromocaoResponse from(Promocao promocao, LocalDateTime agora) {
        return new PromocaoResponse(
                promocao.getIdPromocao(), promocao.getNome(), promocao.getDescricao(),
                promocao.getAbrangencia(),
                promocao.getCondominio() != null ? promocao.getCondominio().getIdCondominio() : null,
                promocao.getCondominio() != null ? promocao.getCondominio().getNome() : null,
                promocao.getTipo(), promocao.getValor(),
                promocao.getInicio().toInstant(ZoneOffset.UTC),
                promocao.getFim().toInstant(ZoneOffset.UTC),
                promocao.isAtivo(), promocao.statusEm(agora), promocao.getPrioridade(),
                promocao.produtosAssociados().stream()
                        .map(p -> new ProdutoResumo(p.getIdProduto(), p.getCodigo(), p.getNome(), p.getPreco()))
                        .toList(),
                promocao.getCreatedAt().toInstant(ZoneOffset.UTC),
                promocao.getUpdatedAt().toInstant(ZoneOffset.UTC));
    }

    public record ProdutoResumo(String id, String codigo, String nome, BigDecimal preco) {
    }
}
