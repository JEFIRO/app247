package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.dto.ProdutoSyncChange;
import com.jefiro.app247.domain.model.dto.ProdutoSyncResponse;
import com.jefiro.app247.infra.repository.EstoqueCondominioRepository;
import com.jefiro.app247.infra.repository.TerminalRepository;
import com.jefiro.app247.infra.repository.PromocaoProdutoRepository;
import com.jefiro.app247.infra.exception.TerminalNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.LinkedHashMap;

@Service
public class ProdutoSyncService {
    private static final Logger log = LoggerFactory.getLogger(ProdutoSyncService.class);

    @Autowired private TerminalRepository terminalRepository;
    @Autowired private EstoqueCondominioRepository estoqueRepository;
    @Autowired private PromocaoProdutoRepository promocaoProdutoRepository;
    @Autowired private PricingService pricingService;

    @Transactional(readOnly = true)
    public ProdutoSyncResponse sincronizar(String uuidTerminal, Optional<Instant> lastSync) {
        var terminal = terminalRepository.findById(uuidTerminal)
                .orElseThrow(TerminalNotFoundException::new);
        var condominio = terminal.getCondominio();
        String condominioId = condominio.getIdCondominio();
        String empresaId = condominio.getEmpresa().getId();
        Instant syncAt = Instant.now();
        LocalDateTime limite = LocalDateTime.ofInstant(syncAt, ZoneOffset.UTC);

        var estoquesPorProduto = new LinkedHashMap<String, com.jefiro.app247.domain.model.EstoqueCondominio>();
        if (lastSync.isPresent()) {
            LocalDateTime cursor = LocalDateTime.ofInstant(lastSync.get(), ZoneOffset.UTC);
            estoqueRepository.findCatalogChanges(condominioId, cursor, limite)
                    .forEach(e -> estoquesPorProduto.put(e.getProduto().getIdProduto(), e));
            promocaoProdutoRepository.findProductIdsWithTemporalTransition(
                            empresaId, condominioId, cursor, limite).stream()
                    .filter(id -> !estoquesPorProduto.containsKey(id))
                    .map(id -> estoqueRepository.findCatalogEntry(condominioId, id))
                    .flatMap(Optional::stream)
                    .forEach(e -> estoquesPorProduto.put(e.getProduto().getIdProduto(), e));
        } else {
            estoqueRepository.findCurrentCatalog(condominioId)
                    .forEach(e -> estoquesPorProduto.put(e.getProduto().getIdProduto(), e));
        }
        var estoques = estoquesPorProduto.values().stream().toList();
        var changes = estoques.stream().map(estoque -> {
            boolean disponivel = Boolean.TRUE.equals(estoque.getAtivo()) && estoque.getProduto().isStatus();
            return ProdutoSyncChange.from(estoque, disponivel
                    ? pricingService.calcular(estoque.getProduto(), condominio, limite)
                    : null);
        }).toList();

        log.info("[PRODUCT-SYNC] terminalUuid={} terminalId={} condominioId={} empresaId={} " +
                        "lastSync={} syncUntil={} fullSync={} associacoesEncontradas={} changes={}",
                uuidTerminal, terminal.getIdTerminal(), condominioId, empresaId,
                lastSync.map(Instant::toString).orElse("<FULL>"), syncAt,
                lastSync.isEmpty(), estoques.size(), changes.size());
        return new ProdutoSyncResponse(syncAt, lastSync.isEmpty(), changes);
    }
}
