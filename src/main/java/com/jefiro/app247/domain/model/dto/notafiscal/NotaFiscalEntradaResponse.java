package com.jefiro.app247.domain.model.dto.notafiscal;

import com.jefiro.app247.domain.model.NotaFiscalEntrada;
import com.jefiro.app247.domain.model.NotaFiscalEntradaItem;
import com.jefiro.app247.domain.model.enum_type.NotaFiscalItemStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record NotaFiscalEntradaResponse(
        String id,
        String chave,
        String numero,
        String serie,
        String emitenteCnpj,
        String emitenteNome,
        Instant dataEmissao,
        BigDecimal valorTotal,
        String status,
        int totalItens,
        int totalPendentes,
        int totalAvisos,
        boolean podeConfirmar,
        int produtosCriados,
        int entradasLancadas,
        Instant createdAt,
        Instant processedAt,
        List<Item> itens
) {
    public record Item(String id, int numeroItem, String codigoFornecedor, String ean, String descricao,
                       String ncm, String cfop, String unidadeComercial, BigDecimal quantidade,
                       BigDecimal valorUnitario, BigDecimal valorTotal, String acao, String produtoId,
                       String produtoNome, String nomeProduto, BigDecimal precoVenda, String categoria,
                       String unidadeMedida, BigDecimal fatorConversao, BigDecimal quantidadeEntrada,
                       BigDecimal custoUnitarioVenda, String status, String mensagem) {
        static Item from(NotaFiscalEntradaItem item) {
            return new Item(item.getId(), item.getNumeroItem(), item.getCodigoFornecedor(), item.getEan(),
                    item.getDescricao(), item.getNcm(), item.getCfop(), item.getUnidadeComercial(),
                    item.getQuantidade(), item.getValorUnitario(), item.getValorTotal(), item.getAcao().name(),
                    item.getProduto() == null ? null : item.getProduto().getIdProduto(),
                    item.getProduto() == null ? null : item.getProduto().getNome(),
                    item.getNomeProduto(), item.getPrecoVenda(),
                    item.getCategoria() == null ? null : item.getCategoria().name(),
                    item.getUnidadeMedida().name(), item.getFatorConversao(), item.getQuantidadeEntrada(),
                    item.getCustoUnitarioVenda(), item.getStatus().name(), item.getMensagem());
        }
    }

    public static NotaFiscalEntradaResponse from(NotaFiscalEntrada nota) {
        List<NotaFiscalEntradaItem> itens = nota.getItens();
        int pendentes = (int) itens.stream().filter(i -> i.getStatus() == NotaFiscalItemStatus.PENDENTE).count();
        int avisos = (int) itens.stream().filter(i -> i.getStatus() == NotaFiscalItemStatus.AVISO).count();
        boolean aberta = nota.getStatus().name().equals("VALIDADA");
        return new NotaFiscalEntradaResponse(nota.getId(), nota.getChave(), nota.getNumero(), nota.getSerie(),
                nota.getEmitenteCnpj(), nota.getEmitenteNome(), nota.getDataEmissao(), nota.getValorTotal(),
                nota.getStatus().name(), itens.size(), pendentes, avisos, aberta && pendentes == 0,
                nota.getProdutosCriados(), nota.getEntradasLancadas(), nota.getCreatedAt(),
                nota.getProcessedAt(), itens.stream().map(Item::from).toList());
    }
}
