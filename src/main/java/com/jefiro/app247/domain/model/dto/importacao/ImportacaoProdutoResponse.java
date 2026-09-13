package com.jefiro.app247.domain.model.dto.importacao;

import com.jefiro.app247.domain.model.ImportacaoProduto;
import com.jefiro.app247.domain.model.ImportacaoProdutoItem;
import com.jefiro.app247.domain.model.enum_type.ImportacaoProdutoStatus;
import com.jefiro.app247.domain.model.enum_type.ModoImportacaoProduto;

import java.time.Instant;
import java.util.List;

public record ImportacaoProdutoResponse(
        String importacaoId,
        String nomeArquivo,
        String hashArquivo,
        ImportacaoProdutoStatus status,
        ModoImportacaoProduto modo,
        int totalLinhas,
        int totalValidas,
        int totalAvisos,
        int totalErros,
        int produtosImportados,
        int barcodesImportados,
        int estoquesCriados,
        Instant createdAt,
        Instant validatedAt,
        Instant processedAt,
        List<Linha> preview
) {
    public record Linha(String aba, int linha, String codigoInterno, String status,
                        String mensagem, String produtoId) {}

    public static ImportacaoProdutoResponse from(ImportacaoProduto importacao,
                                                  List<ImportacaoProdutoItem> itens) {
        return new ImportacaoProdutoResponse(importacao.getId(), importacao.getNomeArquivo(),
                importacao.getHashArquivo(), importacao.getStatus(), importacao.getModo(),
                importacao.getTotalLinhas(), importacao.getTotalValidas(), importacao.getTotalAvisos(),
                importacao.getTotalErros(), importacao.getProdutosImportados(), importacao.getBarcodesImportados(),
                importacao.getEstoquesCriados(), importacao.getCreatedAt(), importacao.getValidatedAt(),
                importacao.getProcessedAt(), itens.stream().map(item -> new Linha(item.getAba(), item.getLinha(),
                        item.getCodigoInterno(), item.getStatus().name(), item.getMensagem(),
                        item.getProduto() == null ? null : item.getProduto().getIdProduto())).toList());
    }
}
