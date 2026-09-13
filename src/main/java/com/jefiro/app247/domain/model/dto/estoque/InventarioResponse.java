package com.jefiro.app247.domain.model.dto.estoque;

import com.jefiro.app247.domain.model.Inventario;
import com.jefiro.app247.domain.model.InventarioItem;
import com.jefiro.app247.domain.model.enum_type.InventarioItemStatus;
import com.jefiro.app247.domain.model.enum_type.InventarioStatus;
import com.jefiro.app247.domain.model.enum_type.TipoLocalEstoque;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record InventarioResponse(
        String id,
        TipoLocalEstoque localTipo,
        String localId,
        String localNome,
        InventarioStatus status,
        String descricao,
        String observacao,
        boolean contagemCega,
        int totalProdutos,
        int contados,
        int pendentes,
        int divergencias,
        int conflitos,
        BigDecimal progressoPercentual,
        Instant createdAt,
        Instant startedAt,
        Instant finalizedAt,
        Instant cancelledAt,
        List<Item> itens
) {
    public record Item(
            String id,
            String produtoId,
            String codigoInterno,
            String codigoBarras,
            List<String> codigosBarras,
            String nome,
            String unidadeMedida,
            String localizacaoPlanograma,
            BigDecimal saldoSistemaSnapshot,
            BigDecimal quantidadeContada,
            BigDecimal diferenca,
            BigDecimal saldoAtualConflito,
            InventarioItemStatus status,
            boolean ajusteAplicado,
            String motivoAjuste,
            Instant contadoEm
    ) {}

    public static InventarioResponse from(Inventario inventario, String localNome,
                                           List<InventarioItem> itens,
                                           java.util.Map<String, String> localizacoes) {
        boolean ocultarSaldo = inventario.isContagemCega()
                && inventario.getStatus() != InventarioStatus.FINALIZADO
                && inventario.getStatus() != InventarioStatus.CANCELADO
                && inventario.getStatus() != InventarioStatus.COM_CONFLITO;
        int contados = (int) itens.stream().filter(i -> i.getQuantidadeContada() != null).count();
        int divergencias = (int) itens.stream().filter(i -> i.getDiferenca() != null
                && i.getDiferenca().signum() != 0).count();
        int conflitos = (int) itens.stream().filter(i -> i.getStatus() == InventarioItemStatus.CONFLITO).count();
        BigDecimal progresso = itens.isEmpty() ? new BigDecimal("100.0")
                : BigDecimal.valueOf(contados).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(itens.size()), 1, java.math.RoundingMode.HALF_UP);
        List<Item> respostas = itens.stream().map(item -> {
            List<String> codigos = item.getProduto().getCodigosBarras().stream()
                    .filter(c -> Boolean.TRUE.equals(c.getAtivo()))
                    .map(c -> c.getCodigoBarras()).toList();
            String principal = item.getProduto().getCodigosBarras().stream()
                    .filter(c -> Boolean.TRUE.equals(c.getAtivo()) && Boolean.TRUE.equals(c.getPrincipal()))
                    .map(c -> c.getCodigoBarras()).findFirst().orElse(null);
            return new Item(item.getId(), item.getProduto().getIdProduto(), item.getProduto().getCodigoInterno(),
                    principal, codigos, item.getProduto().getNome(), item.getProduto().getUnidadeMedida().name(),
                    localizacoes.get(item.getProduto().getIdProduto()),
                    ocultarSaldo ? null : item.getSaldoSistemaSnapshot(), item.getQuantidadeContada(),
                    ocultarSaldo ? null : item.getDiferenca(), item.getSaldoAtualConflito(), item.getStatus(),
                    item.isAjusteAplicado(), item.getMotivoAjuste(), item.getContadoEm());
        }).toList();
        return new InventarioResponse(inventario.getId(), inventario.getLocalTipo(), inventario.getLocalId(),
                localNome, inventario.getStatus(), inventario.getDescricao(), inventario.getObservacao(),
                inventario.isContagemCega(), itens.size(), contados, itens.size() - contados,
                divergencias, conflitos, progresso, inventario.getCreatedAt(), inventario.getStartedAt(),
                inventario.getFinalizedAt(), inventario.getCancelledAt(), respostas);
    }
}
