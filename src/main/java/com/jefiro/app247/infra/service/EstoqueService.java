package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.*;
import com.jefiro.app247.domain.model.dto.estoque.EstoqueResponse;
import com.jefiro.app247.domain.model.dto.estoque.MovimentacaoEstoqueResponse;
import com.jefiro.app247.domain.model.dto.ProdutoTerminalResponse;
import com.jefiro.app247.domain.model.enum_type.TipoMovimentacaoEstoque;
import com.jefiro.app247.domain.model.enum_type.ProdutoCatalogChangeReason;
import com.jefiro.app247.infra.event.ProdutoCatalogChangedEvent;
import com.jefiro.app247.infra.repository.EstoqueCondominioRepository;
import com.jefiro.app247.infra.repository.MovimentacaoEstoqueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Service
public class EstoqueService {
    private static final Logger log = LoggerFactory.getLogger(EstoqueService.class);
    @Autowired private EstoqueCondominioRepository estoqueRepository;
    @Autowired private MovimentacaoEstoqueRepository movimentacaoRepository;
    @Autowired private CondominioService condominioService;
    @Autowired private ProdutoService produtoService;
    @Autowired private TerminalService terminalService;
    @Autowired private ApplicationEventPublisher eventPublisher;
    @Autowired private PricingService pricingService;

    public List<ProdutoTerminalResponse> listarProdutosDoTerminal(String terminalId) {
        var terminal = terminalService.getTerminal(terminalId);
        return estoqueRepository.findAllByCondominioIdCondominioAndCondominioEmpresaId(
                        terminal.getCondominio().getIdCondominio(),
                        terminal.getCondominio().getEmpresa().getId())
                .stream()
                .filter(estoque -> Boolean.TRUE.equals(estoque.getAtivo()))
                .filter(estoque -> estoque.getProduto().isStatus())
                .map(estoque -> new ProdutoTerminalResponse(estoque, pricingService.calcular(
                        estoque.getProduto(), terminal.getCondominio(),
                        java.time.LocalDateTime.now(java.time.ZoneOffset.UTC))))
                .toList();
    }

    @Transactional
    public EstoqueResponse disponibilizar(String condominioId, String produtoId, BigDecimal quantidade) {
        return disponibilizar(condominioId, produtoId, quantidade, true);
    }

    @Transactional
    public EstoqueResponse disponibilizar(String condominioId, String produtoId, BigDecimal quantidade,
                                           Boolean ativoSolicitado) {
        String empresaId = EmpresaContext.require();
        boolean ativo = ativoSolicitado == null || ativoSolicitado;
        Condominio condominio = condominioService.buscarDoTenant(condominioId, empresaId);
        Produto produto = produtoService.buscarPorIdDoTenant(produtoId, empresaId);
        var existente = estoqueRepository.findByCondominioIdCondominioAndProdutoIdProduto(condominioId, produtoId);
        if (existente.isPresent() && Boolean.TRUE.equals(existente.get().getAtivo())) {
            throw new IllegalStateException("Produto já está disponível no condomínio");
        }
        if (existente.isPresent()) {
            EstoqueCondominio estoque = existente.get();
            estoque.alterarDisponibilidade(ativo);
            estoqueRepository.saveAndFlush(estoque);
            movimentar(estoque, quantidade, TipoMovimentacaoEstoque.ENTRADA,
                    null, null, "Reativação no condomínio", null);
            if (ativo) {
                publicarCatalogo(estoque, ProdutoCatalogChangeReason.PRODUCT_AVAILABILITY_CHANGED);
            }
            return new EstoqueResponse(estoque);
        }
        EstoqueCondominio estoque = new EstoqueCondominio();
        estoque.setCondominio(condominio);
        estoque.setProduto(produto);
        estoque.setQuantidade(BigDecimal.ZERO);
        estoque.alterarDisponibilidade(ativo);
        estoque = estoqueRepository.saveAndFlush(estoque);
        movimentar(estoque, quantidade, TipoMovimentacaoEstoque.ENTRADA,
                null, null, "Estoque inicial", null);
        if (ativo) {
            publicarCatalogo(estoque, ProdutoCatalogChangeReason.PRODUCT_ASSIGNED_TO_CONDOMINIUM);
        }
        return new EstoqueResponse(estoque);
    }

    @Transactional
    public EstoqueResponse remover(String condominioId, String produtoId) {
        EstoqueCondominio estoque = estoqueDoTenantParaAtualizacao(condominioId, produtoId);
        if (!Boolean.TRUE.equals(estoque.getAtivo())) return new EstoqueResponse(estoque);
        estoque.alterarDisponibilidade(false);
        estoqueRepository.saveAndFlush(estoque);
        publicarCatalogo(estoque, ProdutoCatalogChangeReason.PRODUCT_REMOVED_FROM_CONDOMINIUM);
        return new EstoqueResponse(estoque);
    }

    public List<EstoqueResponse> listarCondominio(String condominioId) {
        String empresaId = EmpresaContext.require();
        condominioService.buscarDoTenant(condominioId, empresaId);
        return estoqueRepository.findAllByCondominioIdCondominioAndCondominioEmpresaId(condominioId, empresaId)
                .stream().map(EstoqueResponse::new).toList();
    }

    public List<EstoqueResponse> listarGeral() {
        return estoqueRepository.somarPorProdutoDaEmpresa(EmpresaContext.require()).stream()
                .map(row -> new EstoqueResponse((String) row[0], (String) row[1], (BigDecimal) row[2], true))
                .toList();
    }

    public List<MovimentacaoEstoqueResponse> listarMovimentacoes(String condominioId) {
        String empresaId = EmpresaContext.require();
        condominioService.buscarDoTenant(condominioId, empresaId);
        return movimentacaoRepository
                .findAllByEstoqueCondominioIdCondominioAndEstoqueCondominioEmpresaIdOrderByCreatedAtDesc(
                        condominioId, empresaId)
                .stream().map(MovimentacaoEstoqueResponse::new).toList();
    }

    @Transactional
    public EstoqueResponse entrada(String condominioId, String produtoId, BigDecimal quantidade, String motivo) {
        if (quantidade.signum() < 0) throw new IllegalArgumentException("Entrada deve ser positiva");
        EstoqueCondominio estoque = estoqueDoTenantParaAtualizacao(condominioId, produtoId);
        movimentar(estoque, quantidade, TipoMovimentacaoEstoque.ENTRADA, null, null, motivo, null);
        return new EstoqueResponse(estoque);
    }

    @Transactional
    public EstoqueResponse ajustar(String condominioId, String produtoId, BigDecimal novaQuantidade, String motivo) {
        EstoqueCondominio estoque = estoqueDoTenantParaAtualizacao(condominioId, produtoId);
        movimentar(estoque, novaQuantidade.subtract(estoque.getQuantidade()), TipoMovimentacaoEstoque.AJUSTE,
                null, null, motivo, null);
        return new EstoqueResponse(estoque);
    }

    @Transactional
    public void reservar(Order order) {
        for (Item item : order.getCarrinho().getItems()) {
            String chave = chave(order, item, "RESERVA");
            if (movimentacaoRepository.existsByChaveIdempotencia(chave)) continue;
            EstoqueCondominio estoque = estoqueRepository.findForUpdate(
                    order.getCarrinho().getTerminal().getCondominio().getIdCondominio(), item.getProduto().getIdProduto())
                    .orElseThrow(() -> new IllegalStateException("Produto não disponível neste condomínio"));
            movimentar(estoque, BigDecimal.valueOf(item.getQuantity()).negate(), TipoMovimentacaoEstoque.RESERVA,
                    order, item, "Reserva do checkout", chave);
        }
    }

    @Transactional
    public void confirmarVenda(Order order) {
        for (Item item : order.getCarrinho().getItems()) {
            String chave = chave(order, item, "VENDA");
            if (movimentacaoRepository.existsByChaveIdempotencia(chave)) continue;
            if (!movimentacaoRepository.existsByChaveIdempotencia(chave(order, item, "RESERVA"))) {
                log.warn("Order paga sem reserva de estoque: order={}, item={}", order.getIdOrder(), item.getIdItem());
                continue;
            }
            EstoqueCondominio estoque = estoqueRepository.findForUpdate(
                    order.getCarrinho().getTerminal().getCondominio().getIdCondominio(), item.getProduto().getIdProduto())
                    .orElseThrow();
            movimentar(estoque, BigDecimal.ZERO, TipoMovimentacaoEstoque.VENDA,
                    order, item, "Reserva confirmada como venda", chave);
        }
    }

    @Transactional
    public void liberar(Order order, boolean cancelamento) {
        for (Item item : order.getCarrinho().getItems()) {
            String chaveLiberacao = chave(order, item, "LIBERACAO");
            if (movimentacaoRepository.existsByChaveIdempotencia(chaveLiberacao)) continue;
            boolean reservada = movimentacaoRepository.existsByChaveIdempotencia(chave(order, item, "RESERVA"));
            if (!reservada) continue;
            EstoqueCondominio estoque = estoqueRepository.findForUpdate(
                    order.getCarrinho().getTerminal().getCondominio().getIdCondominio(), item.getProduto().getIdProduto())
                    .orElseThrow();
            movimentar(estoque, BigDecimal.valueOf(item.getQuantity()), cancelamento
                            ? TipoMovimentacaoEstoque.CANCELAMENTO : TipoMovimentacaoEstoque.LIBERACAO_RESERVA,
                    order, item, cancelamento ? "Cancelamento/reembolso" : "Pagamento não concluído", chaveLiberacao);
        }
    }

    private EstoqueCondominio estoqueDoTenantParaAtualizacao(String condominioId, String produtoId) {
        String empresaId = EmpresaContext.require();
        condominioService.buscarDoTenant(condominioId, empresaId);
        produtoService.buscarPorIdDoTenant(produtoId, empresaId);
        return estoqueRepository.findForUpdate(condominioId, produtoId)
                .orElseThrow(() -> new IllegalArgumentException("Produto não está disponível no condomínio"));
    }

    private void movimentar(EstoqueCondominio estoque, BigDecimal delta, TipoMovimentacaoEstoque tipo,
                            Order order, Item item, String motivo, String chave) {
        BigDecimal anterior = estoque.getQuantidade();
        BigDecimal posterior = anterior.add(delta);
        estoque.setQuantidade(posterior);
        estoqueRepository.save(estoque);
        MovimentacaoEstoque movimento = new MovimentacaoEstoque();
        movimento.setEstoque(estoque);
        movimento.setTipo(tipo);
        movimento.setQuantidade(delta.abs());
        movimento.setQuantidadeAnterior(anterior);
        movimento.setQuantidadePosterior(posterior);
        movimento.setOrder(order);
        movimento.setItem(item);
        movimento.setMotivo(motivo);
        movimento.setChaveIdempotencia(chave != null ? chave : "MANUAL:" + java.util.UUID.randomUUID());
        movimentacaoRepository.save(movimento);
        if (posterior.signum() < 0) {
            log.warn("Estoque negativo: condominio={}, produto={}, quantidade={}",
                    estoque.getCondominio().getIdCondominio(), estoque.getProduto().getIdProduto(), posterior);
        }
    }

    private String chave(Order order, Item item, String fase) {
        return order.getIdOrder() + ":" + item.getIdItem() + ":" + fase;
    }

    private void publicarCatalogo(EstoqueCondominio estoque, ProdutoCatalogChangeReason motivo) {
        eventPublisher.publishEvent(new ProdutoCatalogChangedEvent(
                estoque.getProduto().getIdProduto(), motivo,
                Set.of(estoque.getCondominio().getIdCondominio())));
    }
}
