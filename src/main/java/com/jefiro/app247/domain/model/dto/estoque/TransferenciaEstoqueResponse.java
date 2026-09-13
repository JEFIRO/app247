package com.jefiro.app247.domain.model.dto.estoque;

import com.jefiro.app247.domain.model.TransferenciaEstoque;
import com.jefiro.app247.domain.model.enum_type.StatusTransferenciaEstoque;
import com.jefiro.app247.domain.model.enum_type.TipoLocalEstoque;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record TransferenciaEstoqueResponse(
        String id,
        TipoLocalEstoque origemTipo,
        String origemId,
        TipoLocalEstoque destinoTipo,
        String destinoId,
        String destinoNome,
        StatusTransferenciaEstoque status,
        String observacao,
        String responsavel,
        Instant createdAt,
        Instant confirmedAt,
        Instant cancelledAt,
        List<Item> itens,
        List<MovimentacaoEstoqueResponse> movimentacoes
) {
    public record Item(String id, String produtoId, String produto, BigDecimal quantidade) {}

    public static TransferenciaEstoqueResponse from(TransferenciaEstoque transferencia, String destinoNome,
                                                     List<MovimentacaoEstoqueResponse> movimentos) {
        return new TransferenciaEstoqueResponse(transferencia.getId(), transferencia.getOrigemTipo(),
                transferencia.getOrigemId(), transferencia.getDestinoTipo(), transferencia.getDestinoId(),
                destinoNome, transferencia.getStatus(), transferencia.getObservacao(),
                transferencia.getCreatedBy() == null ? null : transferencia.getCreatedBy().getNome(),
                transferencia.getCreatedAt(), transferencia.getConfirmedAt(), transferencia.getCancelledAt(),
                transferencia.getItens().stream().map(item -> new Item(item.getId(),
                        item.getProduto().getIdProduto(), item.getProduto().getNome(), item.getQuantidade())).toList(),
                movimentos);
    }
}
