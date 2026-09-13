package com.jefiro.app247.domain.model.dto.estoque;

import com.jefiro.app247.domain.model.MovimentacaoEstoque;
import com.jefiro.app247.domain.model.enum_type.TipoMovimentacaoEstoque;
import java.math.BigDecimal;
import java.time.Instant;

public record MovimentacaoEstoqueResponse(String id, String localTipo, String localId, String produtoId, TipoMovimentacaoEstoque tipo,
        BigDecimal quantidade, BigDecimal quantidadeAnterior, BigDecimal quantidadePosterior,
        String orderId, String itemId, String transferenciaId, String inventarioId,
        String inventarioItemId, String responsavel, String motivo, Instant createdAt) {
    public MovimentacaoEstoqueResponse(MovimentacaoEstoque movimento) {
        this(movimento.getId(),
                movimento.getEstoqueEmpresa() == null ? "CONDOMINIO" : "ESTOQUE_EMPRESA",
                movimento.getEstoqueEmpresa() == null ? movimento.getEstoque().getCondominio().getIdCondominio() : movimento.getEmpresa().getId(),
                movimento.getEstoqueEmpresa() == null ? movimento.getEstoque().getProduto().getIdProduto() : movimento.getEstoqueEmpresa().getProduto().getIdProduto(),
                movimento.getTipo(),
                movimento.getQuantidade(), movimento.getQuantidadeAnterior(), movimento.getQuantidadePosterior(),
                movimento.getOrder() == null ? null : movimento.getOrder().getIdOrder(),
                movimento.getItem() == null ? null : movimento.getItem().getIdItem(),
                movimento.getTransferencia() == null ? null : movimento.getTransferencia().getId(),
                movimento.getInventario() == null ? null : movimento.getInventario().getId(),
                movimento.getInventarioItem() == null ? null : movimento.getInventarioItem().getId(),
                movimento.getCreatedBy() == null ? null : movimento.getCreatedBy().getNome(),
                movimento.getMotivo(), movimento.getCreatedAt());
    }
}
