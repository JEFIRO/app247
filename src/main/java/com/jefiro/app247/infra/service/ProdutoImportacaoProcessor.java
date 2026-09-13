package com.jefiro.app247.infra.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jefiro.app247.domain.model.ImportacaoProdutoItem;
import com.jefiro.app247.domain.model.dto.importacao.ImportacaoProdutoPayload;
import com.jefiro.app247.domain.model.enum_type.ImportacaoProdutoItemStatus;
import com.jefiro.app247.domain.model.enum_type.ImportacaoProdutoStatus;
import com.jefiro.app247.infra.event.ImportacaoProdutoSolicitadaEvent;
import com.jefiro.app247.infra.repository.ImportacaoProdutoItemRepository;
import com.jefiro.app247.infra.repository.ImportacaoProdutoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class ProdutoImportacaoProcessor {
    private static final Logger log = LoggerFactory.getLogger(ProdutoImportacaoProcessor.class);
    @Autowired private ImportacaoProdutoRepository importacaoRepository;
    @Autowired private ImportacaoProdutoItemRepository itemRepository;
    @Autowired private ProdutoImportacaoItemProcessor itemProcessor;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private AuditLogService auditLogService;

    @Async("productImportExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void ouvir(ImportacaoProdutoSolicitadaEvent event) {
        executarSeguro(event.importacaoId());
    }

    @Scheduled(
            fixedDelayString = "${product-import.recovery-delay-ms:60000}",
            initialDelayString = "${product-import.recovery-initial-delay-ms:60000}")
    public void recuperarPendentes() {
        importacaoRepository.findAllByStatusOrderByCreatedAtAsc(
                        ImportacaoProdutoStatus.PROCESSANDO, PageRequest.of(0, 20))
                .forEach(importacao -> executarSeguro(importacao.getId()));
    }

    private void executarSeguro(String importacaoId) {
        try {
            processar(importacaoId);
        } catch (Exception ex) {
            log.error("[PRODUCT-IMPORT] falha inesperada importacaoId={}", importacaoId, ex);
            transactionTemplate.executeWithoutResult(status -> marcarFalha(importacaoId, ex));
        }
    }

    public void processar(String importacaoId) {
        var importacao = importacaoRepository.findById(importacaoId).orElse(null);
        if (importacao == null || importacao.getStatus() != ImportacaoProdutoStatus.PROCESSANDO) return;
        List<ImportacaoProdutoItem> itens = itemRepository
                .findAllByImportacaoIdAndStatusAndAbaOrderByLinhaAsc(
                        importacaoId, ImportacaoProdutoItemStatus.VALIDO, ProdutoPlanilhaService.PRODUTOS);
        for (ImportacaoProdutoItem item : itens) {
            try { itemProcessor.importar(item.getId()); }
            catch (Exception ex) { itemProcessor.marcarErro(item.getId(), ex); }
        }
        transactionTemplate.executeWithoutResult(status -> finalizar(importacaoId));
    }

    private void finalizar(String importacaoId) {
        var importacao = importacaoRepository.findForUpdateById(importacaoId)
                .orElseThrow(() -> new IllegalStateException("Importação não encontrada ao finalizar"));
        if (importacao.getStatus() != ImportacaoProdutoStatus.PROCESSANDO) return;
        List<ImportacaoProdutoItem> produtos = itemRepository
                .findAllByImportacaoIdAndStatusAndAbaOrderByLinhaAsc(
                        importacaoId, ImportacaoProdutoItemStatus.IMPORTADO, ProdutoPlanilhaService.PRODUTOS);
        long erros = itemRepository.countByImportacaoIdAndStatus(importacaoId, ImportacaoProdutoItemStatus.ERRO);
        int barcodes = 0;
        int estoques = 0;
        for (ImportacaoProdutoItem item : produtos) {
            try {
                ImportacaoProdutoPayload payload = objectMapper.readValue(
                        item.getDadosNormalizados(), ImportacaoProdutoPayload.class);
                barcodes += payload.codigosBarras().size();
                if (payload.estoqueInicial() != null) estoques++;
            } catch (Exception ex) {
                throw new IllegalStateException("Payload importado não pôde ser auditado", ex);
            }
        }
        importacao.setProdutosImportados(produtos.size());
        importacao.setBarcodesImportados(barcodes);
        importacao.setEstoquesCriados(estoques);
        importacao.setTotalErros(Math.toIntExact(erros));
        importacao.setStatus(erros == 0 ? ImportacaoProdutoStatus.CONCLUIDA
                : ImportacaoProdutoStatus.CONCLUIDA_COM_ERROS);
        importacao.setProcessedAt(Instant.now());
        importacaoRepository.save(importacao);
        auditLogService.record(importacao.getEmpresa(), erros == 0
                        ? "IMPORTACAO_PRODUTO_CONCLUIDA" : "IMPORTACAO_PRODUTO_FALHOU",
                "ImportacaoProduto", importacaoId, Map.of("status", "PROCESSANDO"),
                Map.of("status", importacao.getStatus(), "produtos", produtos.size(), "erros", erros), Map.of());
    }

    private void marcarFalha(String importacaoId, Throwable error) {
        importacaoRepository.findForUpdateById(importacaoId).ifPresent(importacao -> {
            if (importacao.getStatus() != ImportacaoProdutoStatus.PROCESSANDO) return;
            String mensagem = error.getMessage() == null ? "Falha inesperada no processamento" : error.getMessage();
            importacao.setStatus(ImportacaoProdutoStatus.FALHOU);
            importacao.setProcessedAt(Instant.now());
            importacaoRepository.save(importacao);
            auditLogService.record(importacao.getEmpresa(), "IMPORTACAO_PRODUTO_FALHOU",
                    "ImportacaoProduto", importacaoId, Map.of("status", "PROCESSANDO"),
                    Map.of("status", "FALHOU"), Map.of("erro", mensagem.substring(0, Math.min(500, mensagem.length()))));
        });
    }
}
