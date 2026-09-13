package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.*;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.dto.estoque.ContagemInventarioRequest;
import com.jefiro.app247.domain.model.dto.estoque.InventarioRequest;
import com.jefiro.app247.domain.model.dto.estoque.InventarioResponse;
import com.jefiro.app247.domain.model.enum_type.*;
import com.jefiro.app247.infra.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

@Service
public class InventarioService {
    @Autowired private InventarioRepository inventarioRepository;
    @Autowired private InventarioItemRepository itemRepository;
    @Autowired private EstoqueEmpresaRepository estoqueEmpresaRepository;
    @Autowired private EstoqueCondominioRepository estoqueCondominioRepository;
    @Autowired private MovimentacaoEstoqueRepository movimentacaoRepository;
    @Autowired private EmpresaRepository empresaRepository;
    @Autowired private CondominioRepository condominioRepository;
    @Autowired private CondominioService condominioService;
    @Autowired private PlanogramaProdutoRepository planogramaProdutoRepository;
    @Autowired private AuditLogService auditLogService;

    @Transactional
    public InventarioResponse iniciar(InventarioRequest request) {
        String empresaId = EmpresaContext.require();
        if (request.localTipo() == TipoLocalEstoque.DEPOSITO) {
            throw new IllegalArgumentException("Local de inventário ainda não suportado");
        }
        Empresa empresa = empresaRepository.getReferenceById(empresaId);
        Inventario inventario = new Inventario();
        inventario.setEmpresa(empresa);
        inventario.setLocalTipo(request.localTipo());
        inventario.setDescricao(request.descricao());
        inventario.setObservacao(request.observacao());
        inventario.setContagemCega(request.contagemCega());
        inventario.setStatus(InventarioStatus.EM_CONTAGEM);
        inventario.setStartedAt(Instant.now());
        inventario.setCreatedBy(usuarioAtual(empresaId));

        if (request.localTipo() == TipoLocalEstoque.ESTOQUE_EMPRESA) {
            inventario.setLocalId(empresaId);
            estoqueEmpresaRepository.findAllByEmpresaIdAndAtivoTrueOrderByProdutoCodigoInterno(empresaId)
                    .forEach(estoque -> inventario.getItens().add(itemCentral(inventario, estoque)));
        } else {
            if (request.localId() == null || request.localId().isBlank()) {
                throw new IllegalArgumentException("Condomínio obrigatório para este inventário");
            }
            Condominio condominio = condominioService.buscarDoTenant(request.localId(), empresaId);
            inventario.setLocalId(condominio.getIdCondominio());
            estoqueCondominioRepository
                    .findAllByCondominioIdCondominioAndCondominioEmpresaIdAndAtivoTrueOrderByProdutoCodigoInterno(
                            condominio.getIdCondominio(), empresaId)
                    .forEach(estoque -> inventario.getItens().add(itemCondominio(inventario, estoque)));
        }
        inventarioRepository.saveAndFlush(inventario);
        auditLogService.record(empresa, "INVENTARIO_CRIADO", "Inventario", inventario.getId(), null,
                Map.of("localTipo", inventario.getLocalTipo(), "localId", inventario.getLocalId(),
                        "itens", inventario.getItens().size(), "contagemCega", inventario.isContagemCega()), Map.of());
        return resposta(inventario);
    }

    @Transactional
    public InventarioResponse contar(String inventarioId, String itemId, ContagemInventarioRequest request) {
        String empresaId = EmpresaContext.require();
        Inventario inventario = inventarioRepository.findForUpdate(inventarioId, empresaId)
                .orElseThrow(() -> new NoSuchElementException("Inventário não encontrado"));
        validarEditavel(inventario);
        InventarioItem item = itemRepository.findForUpdate(itemId, inventarioId, empresaId)
                .orElseThrow(() -> new NoSuchElementException("Item do inventário não encontrado"));
        if (item.isAjusteAplicado()) throw new IllegalStateException("Este item já teve seu ajuste aplicado");

        if (item.getStatus() == InventarioItemStatus.CONFLITO) atualizarSnapshot(item, empresaId);
        BigDecimal contada = quantidade(request.quantidadeContada());
        item.setQuantidadeContada(contada);
        item.setDiferenca(contada.subtract(item.getSaldoSistemaSnapshot()).setScale(3));
        item.setStatus(classificar(item.getDiferenca()));
        item.setSaldoAtualConflito(null);
        item.setMotivoAjuste(request.motivoAjuste());
        item.setContadoPor(usuarioAtual(empresaId));
        item.setContadoEm(Instant.now());
        itemRepository.save(item);
        if (inventario.getStatus() == InventarioStatus.COM_CONFLITO) {
            inventario.setStatus(InventarioStatus.EM_CONTAGEM);
        }
        return resposta(inventario);
    }

    @Transactional
    public InventarioResponse finalizar(String id) {
        String empresaId = EmpresaContext.require();
        Inventario inventario = inventarioRepository.findForUpdate(id, empresaId)
                .orElseThrow(() -> new NoSuchElementException("Inventário não encontrado"));
        if (inventario.getStatus() == InventarioStatus.FINALIZADO) return resposta(inventario);
        validarEditavel(inventario);
        List<InventarioItem> itens = itemRepository
                .findAllByInventarioIdAndEmpresaIdOrderByProdutoCodigoInterno(id, empresaId);
        if (itens.stream().anyMatch(item -> item.getQuantidadeContada() == null)) {
            throw new IllegalStateException("Todos os produtos precisam ser contados antes da finalização");
        }
        User responsavel = usuarioAtual(empresaId);
        boolean conflito = false;
        for (InventarioItem item : itens) {
            if (item.isAjusteAplicado()) continue;
            SaldoAtual atual = saldoAtualComLock(item, empresaId);
            if (!Objects.equals(atual.version(), item.getEstoqueVersionSnapshot())) {
                item.setStatus(InventarioItemStatus.CONFLITO);
                item.setSaldoAtualConflito(atual.quantidade());
                itemRepository.save(item);
                conflito = true;
                continue;
            }
            BigDecimal delta = item.getQuantidadeContada().subtract(atual.quantidade()).setScale(3);
            item.setDiferenca(item.getQuantidadeContada().subtract(item.getSaldoSistemaSnapshot()).setScale(3));
            if (delta.signum() != 0) {
                atual.alterar(item.getQuantidadeContada());
                MovimentacaoEstoque movimento = new MovimentacaoEstoque();
                movimento.setEmpresa(inventario.getEmpresa());
                movimento.setEstoqueEmpresa(item.getEstoqueEmpresa());
                movimento.setEstoque(item.getEstoqueCondominio());
                movimento.setInventario(inventario);
                movimento.setInventarioItem(item);
                movimento.setTipo(TipoMovimentacaoEstoque.AJUSTE_INVENTARIO);
                movimento.setQuantidade(delta);
                movimento.setQuantidadeAnterior(atual.quantidadeAnterior());
                movimento.setQuantidadePosterior(item.getQuantidadeContada());
                movimento.setMotivo(item.getMotivoAjuste() == null ? "Ajuste de inventário" : item.getMotivoAjuste());
                movimento.setCreatedBy(responsavel);
                movimento.setChaveIdempotencia("INVENTARIO:" + item.getId());
                movimentacaoRepository.save(movimento);
                auditLogService.record(inventario.getEmpresa(), "AJUSTE_INVENTARIO", "InventarioItem", item.getId(),
                        Map.of("saldo", atual.quantidadeAnterior()), Map.of("saldo", item.getQuantidadeContada()),
                        Map.of("inventarioId", inventario.getId(), "movimentacaoId", movimento.getId(),
                                "diferenca", delta));
            }
            item.setAjusteAplicado(true);
            item.setAjusteAplicadoEm(Instant.now());
            item.setSaldoAtualConflito(null);
            itemRepository.save(item);
        }
        if (conflito) {
            inventario.setStatus(InventarioStatus.COM_CONFLITO);
        } else {
            inventario.setStatus(InventarioStatus.FINALIZADO);
            inventario.setFinalizedAt(Instant.now());
            inventario.setFinalizedBy(responsavel);
            auditLogService.record(inventario.getEmpresa(), "INVENTARIO_FINALIZADO", "Inventario", id,
                    Map.of("status", "EM_CONTAGEM"), Map.of("status", "FINALIZADO"),
                    Map.of("itens", itens.size()));
        }
        inventarioRepository.saveAndFlush(inventario);
        return resposta(inventario);
    }

    @Transactional
    public InventarioResponse cancelar(String id) {
        String empresaId = EmpresaContext.require();
        Inventario inventario = inventarioRepository.findForUpdate(id, empresaId)
                .orElseThrow(() -> new NoSuchElementException("Inventário não encontrado"));
        if (inventario.getStatus() == InventarioStatus.CANCELADO) return resposta(inventario);
        if (inventario.getStatus() == InventarioStatus.FINALIZADO) {
            throw new IllegalStateException("Inventário finalizado não pode ser cancelado");
        }
        boolean possuiAjuste = itemRepository.findAllByInventarioIdAndEmpresaIdOrderByProdutoCodigoInterno(id, empresaId)
                .stream().anyMatch(InventarioItem::isAjusteAplicado);
        if (possuiAjuste) throw new IllegalStateException("Inventário com ajustes aplicados deve ter os conflitos revisados");
        inventario.setStatus(InventarioStatus.CANCELADO);
        inventario.setCancelledAt(Instant.now());
        auditLogService.record(inventario.getEmpresa(), "INVENTARIO_CANCELADO", "Inventario", id,
                null, Map.of("status", "CANCELADO"), Map.of());
        return resposta(inventario);
    }

    @Transactional(readOnly = true)
    public InventarioResponse buscar(String id) {
        Inventario inventario = inventarioRepository.findByIdAndEmpresaId(id, EmpresaContext.require())
                .orElseThrow(() -> new NoSuchElementException("Inventário não encontrado"));
        return resposta(inventario);
    }

    @Transactional(readOnly = true)
    public Page<InventarioResponse> listar(InventarioStatus status, TipoLocalEstoque localTipo, Pageable pageable) {
        return inventarioRepository.pesquisar(EmpresaContext.require(), status, localTipo, pageable)
                .map(this::resposta);
    }

    private InventarioItem itemCentral(Inventario inventario, EstoqueEmpresa estoque) {
        InventarioItem item = itemBase(inventario, estoque.getProduto(), estoque.getQuantidade(),
                estoque.getLockVersion(), estoque.getUpdatedAt());
        item.setEstoqueEmpresa(estoque);
        return item;
    }

    private InventarioItem itemCondominio(Inventario inventario, EstoqueCondominio estoque) {
        InventarioItem item = itemBase(inventario, estoque.getProduto(), estoque.getQuantidade(),
                estoque.getLockVersion(), estoque.getUpdatedAt());
        item.setEstoqueCondominio(estoque);
        return item;
    }

    private InventarioItem itemBase(Inventario inventario, Produto produto, BigDecimal saldo,
                                     Long version, Instant updatedAt) {
        InventarioItem item = new InventarioItem();
        item.setInventario(inventario);
        item.setEmpresa(inventario.getEmpresa());
        item.setProduto(produto);
        item.setSaldoSistemaSnapshot(saldo.setScale(3));
        item.setEstoqueVersionSnapshot(version == null ? 0L : version);
        item.setEstoqueUpdatedAtSnapshot(updatedAt);
        return item;
    }

    private void atualizarSnapshot(InventarioItem item, String empresaId) {
        SaldoAtual atual = saldoAtualComLock(item, empresaId);
        item.setSaldoSistemaSnapshot(atual.quantidade());
        item.setEstoqueVersionSnapshot(atual.version());
        item.setEstoqueUpdatedAtSnapshot(atual.updatedAt());
    }

    private SaldoAtual saldoAtualComLock(InventarioItem item, String empresaId) {
        if (item.getEstoqueEmpresa() != null) {
            EstoqueEmpresa estoque = estoqueEmpresaRepository.findForUpdate(empresaId, item.getProduto().getIdProduto())
                    .orElseThrow(() -> new IllegalStateException("Estoque central do item não existe mais"));
            return new SaldoAtual(estoque.getQuantidade(), valorVersion(estoque.getLockVersion()), estoque.getUpdatedAt(),
                    novoSaldo -> { estoque.setQuantidade(novoSaldo); estoqueEmpresaRepository.save(estoque); });
        }
        EstoqueCondominio estoque = estoqueCondominioRepository.findForUpdate(
                        item.getInventario().getLocalId(), item.getProduto().getIdProduto())
                .orElseThrow(() -> new IllegalStateException("Estoque do condomínio não existe mais"));
        if (!empresaId.equals(estoque.getEmpresa().getId())) throw new IllegalStateException("Estoque cross-tenant");
        return new SaldoAtual(estoque.getQuantidade(), valorVersion(estoque.getLockVersion()), estoque.getUpdatedAt(),
                novoSaldo -> { estoque.setQuantidade(novoSaldo); estoqueCondominioRepository.save(estoque); });
    }

    private InventarioResponse resposta(Inventario inventario) {
        String empresaId = inventario.getEmpresa().getId();
        List<InventarioItem> itens = itemRepository
                .findAllByInventarioIdAndEmpresaIdOrderByProdutoCodigoInterno(inventario.getId(), empresaId);
        List<String> produtos = itens.stream().map(item -> item.getProduto().getIdProduto()).toList();
        Map<String, String> localizacoes = new HashMap<>();
        if (inventario.getLocalTipo() == TipoLocalEstoque.ESTOQUE_EMPRESA && !produtos.isEmpty()) {
            planogramaProdutoRepository.findAllByEmpresaIdAndProdutoIdProdutoInAndAtivoTrue(empresaId, produtos)
                    .stream().sorted(Comparator.comparing(p -> p.getPosicao().getOrdem()))
                    .forEach(p -> localizacoes.putIfAbsent(p.getProduto().getIdProduto(),
                            com.jefiro.app247.domain.model.dto.estoque.PlanogramaResponse.localizacao(p.getPosicao())));
        }
        return InventarioResponse.from(inventario, nomeLocal(inventario), itens, localizacoes);
    }

    private String nomeLocal(Inventario inventario) {
        if (inventario.getLocalTipo() == TipoLocalEstoque.ESTOQUE_EMPRESA) return "Estoque Central";
        return condominioRepository.findByIdCondominioAndEmpresaId(inventario.getLocalId(), inventario.getEmpresa().getId())
                .map(Condominio::getNome).orElse(inventario.getLocalId());
    }

    private void validarEditavel(Inventario inventario) {
        if (inventario.getStatus() == InventarioStatus.CANCELADO
                || inventario.getStatus() == InventarioStatus.FINALIZADO) {
            throw new IllegalStateException("Inventário não está aberto para contagem");
        }
    }

    private InventarioItemStatus classificar(BigDecimal diferenca) {
        return diferenca.signum() == 0 ? InventarioItemStatus.OK
                : diferenca.signum() < 0 ? InventarioItemStatus.FALTA : InventarioItemStatus.SOBRA;
    }

    private BigDecimal quantidade(BigDecimal valor) {
        if (valor == null) throw new IllegalArgumentException("Quantidade contada obrigatória");
        try { return valor.setScale(3, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException ex) { throw new IllegalArgumentException("Quantidade aceita no máximo 3 casas decimais"); }
    }

    private long valorVersion(Long version) { return version == null ? 0L : version; }

    private User usuarioAtual(String empresaId) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user
                && user.getEmpresa() != null && empresaId.equals(user.getEmpresa().getId())) return user;
        return null;
    }

    private record SaldoAtual(BigDecimal quantidade, long version, Instant updatedAt,
                              java.util.function.Consumer<BigDecimal> setter) {
        BigDecimal quantidadeAnterior() { return quantidade; }
        void alterar(BigDecimal novoSaldo) { setter.accept(novoSaldo); }
    }
}
