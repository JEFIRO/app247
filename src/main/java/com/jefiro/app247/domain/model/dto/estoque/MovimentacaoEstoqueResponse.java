package com.jefiro.app247.domain.model.dto.estoque;

import com.jefiro.app247.domain.model.MovimentacaoEstoque;
import com.jefiro.app247.domain.model.enum_type.TipoMovimentacaoEstoque;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MovimentacaoEstoqueResponse(String id, String produtoId, TipoMovimentacaoEstoque tipo,
        BigDecimal quantidade, BigDecimal quantidadeAnterior, BigDecimal quantidadePosterior,
        String orderId, String itemId, String motivo, LocalDateTime createdAt) {
    public MovimentacaoEstoqueResponse(MovimentacaoEstoque movimento) {
        this(movimento.getId(), movimento.getEstoque().getProduto().getIdProduto(), movimento.getTipo(),
                movimento.getQuantidade(), movimento.getQuantidadeAnterior(), movimento.getQuantidadePosterior(),
                movimento.getOrder() == null ? null : movimento.getOrder().getIdOrder(),
                movimento.getItem() == null ? null : movimento.getItem().getIdItem(),
                movimento.getMotivo(), movimento.getCreatedAt());
    }
}
