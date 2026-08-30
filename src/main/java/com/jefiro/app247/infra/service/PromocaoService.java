package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.*;
import com.jefiro.app247.domain.model.dto.PromocaoRequest;
import com.jefiro.app247.domain.model.dto.PromocaoResponse;
import com.jefiro.app247.domain.model.enum_type.*;
import com.jefiro.app247.infra.event.ProdutoCatalogChangedEvent;
import com.jefiro.app247.infra.repository.EstoqueCondominioRepository;
import com.jefiro.app247.infra.repository.PromocaoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
public class PromocaoService {
    @Autowired private PromocaoRepository promocaoRepository;
    @Autowired private ProdutoService produtoService;
    @Autowired private EmpresaService empresaService;
    @Autowired private CondominioService condominioService;
    @Autowired private EstoqueCondominioRepository estoqueRepository;
    @Autowired private ApplicationEventPublisher eventPublisher;

    @Transactional
    public PromocaoResponse criar(PromocaoRequest request) {
        String empresaId = EmpresaContext.require();
        Promocao promocao = new Promocao();
        promocao.setEmpresa(empresaService.getEmpresa(empresaId));
        aplicar(promocao, request, empresaId);
        Promocao salva = promocaoRepository.saveAndFlush(promocao);
        notificar(destinos(salva), ProdutoCatalogChangeReason.PROMOTION_CREATED);
        return response(salva);
    }

    @Transactional
    public PromocaoResponse atualizar(String id, PromocaoRequest request) {
        String empresaId = EmpresaContext.require();
        Promocao promocao = buscarEntidade(id, empresaId);
        Map<String, Set<String>> antes = destinos(promocao);
        aplicar(promocao, request, empresaId);
        Promocao salva = promocaoRepository.saveAndFlush(promocao);
        notificar(merge(antes, destinos(salva)), ProdutoCatalogChangeReason.PROMOTION_UPDATED);
        return response(salva);
    }

    @Transactional
    public PromocaoResponse alterarStatus(String id, boolean ativo) {
        String empresaId = EmpresaContext.require();
        Promocao promocao = buscarEntidade(id, empresaId);
        if (promocao.isAtivo() == ativo) return response(promocao);
        promocao.setAtivo(ativo);
        Promocao salva = promocaoRepository.saveAndFlush(promocao);
        notificar(destinos(salva), ProdutoCatalogChangeReason.PROMOTION_STATUS_CHANGED);
        return response(salva);
    }

    @Transactional(readOnly = true)
    public PromocaoResponse buscar(String id) {
        return response(buscarEntidade(id, EmpresaContext.require()));
    }

    @Transactional(readOnly = true)
    public List<PromocaoResponse> listar(AbrangenciaPromocao abrangencia, StatusPromocao status,
                                          String condominioId, String produtoId) {
        String empresaId = EmpresaContext.require();
        LocalDateTime agora = agora();
        return promocaoRepository.findDistinctByEmpresaIdOrderByCreatedAtDesc(empresaId).stream()
                .filter(p -> abrangencia == null || p.getAbrangencia() == abrangencia)
                .filter(p -> status == null || p.statusEm(agora) == status)
                .filter(p -> condominioId == null || condominioId.isBlank()
                        || (p.getCondominio() != null
                        && condominioId.equals(p.getCondominio().getIdCondominio())))
                .filter(p -> produtoId == null || produtoId.isBlank()
                        || p.produtosAssociados().stream()
                        .anyMatch(produto -> produtoId.equals(produto.getIdProduto())))
                .map(p -> PromocaoResponse.from(p, agora))
                .toList();
    }

    @Transactional
    public void publicarTransicao(Promocao promocao) {
        notificar(destinos(promocao), ProdutoCatalogChangeReason.PROMOTION_TIME_TRANSITION);
    }

    private void aplicar(Promocao promocao, PromocaoRequest request, String empresaId) {
        validarPeriodo(request.inicio(), request.fim());
        promocao.setNome(request.nome().trim());
        promocao.setDescricao(request.descricao() == null || request.descricao().isBlank()
                ? null : request.descricao().trim());
        promocao.setAbrangencia(request.abrangencia());
        promocao.setCondominio(resolverCondominio(request, empresaId));
        promocao.setTipo(request.tipo());
        promocao.setValor(MoneyPolicy.persistence(request.valor()));
        promocao.setInicio(LocalDateTime.ofInstant(request.inicio(), ZoneOffset.UTC));
        promocao.setFim(LocalDateTime.ofInstant(request.fim(), ZoneOffset.UTC));
        if (request.ativo() != null) {
            promocao.setAtivo(request.ativo());
        } else if (promocao.getIdPromocao() == null) {
            promocao.setAtivo(true);
        }
        promocao.setPrioridade(request.prioridade() == null ? 0 : request.prioridade());

        LinkedHashMap<String, Produto> produtos = new LinkedHashMap<>();
        for (String produtoId : request.produtoIds()) {
            Produto produto = produtoService.buscarPorIdDoTenant(produtoId, empresaId);
            validarProduto(promocao, produto);
            produtos.put(produto.getIdProduto(), produto);
        }

        promocao.getProdutos().removeIf(
                associacao -> !produtos.containsKey(associacao.getProduto().getIdProduto()));
        Set<String> existentes = new HashSet<>();
        promocao.getProdutos().forEach(a -> existentes.add(a.getProduto().getIdProduto()));
        produtos.values().stream()
                .filter(produto -> !existentes.contains(produto.getIdProduto()))
                .forEach(promocao::adicionarProduto);
    }

    private Condominio resolverCondominio(PromocaoRequest request, String empresaId) {
        if (request.abrangencia() == AbrangenciaPromocao.EMPRESA) {
            if (request.condominioId() != null && !request.condominioId().isBlank()) {
                throw new IllegalArgumentException("Promoção de empresa não deve informar condomínio");
            }
            return null;
        }
        if (request.condominioId() == null || request.condominioId().isBlank()) {
            throw new IllegalArgumentException("Condomínio é obrigatório para esta abrangência");
        }
        return condominioService.buscarDoTenant(request.condominioId(), empresaId);
    }

    private void validarProduto(Promocao promocao, Produto produto) {
        if (!promocao.getEmpresa().getId().equals(produto.getEmpresa().getId())) {
            throw new IllegalArgumentException("Produto pertence a outra empresa");
        }
        if (promocao.getAbrangencia() == AbrangenciaPromocao.CONDOMINIO
                && estoqueRepository.findByCondominioIdCondominioAndProdutoIdProdutoAndAtivoTrue(
                promocao.getCondominio().getIdCondominio(), produto.getIdProduto()).isEmpty()) {
            throw new IllegalArgumentException("Produto não está disponível neste condomínio.");
        }
        BigDecimal valor = promocao.getValor();
        switch (promocao.getTipo()) {
            case PERCENTUAL -> {
                if (valor.signum() <= 0 || valor.compareTo(new BigDecimal("100")) > 0) {
                    throw new IllegalArgumentException("Percentual deve ser maior que zero e menor ou igual a 100");
                }
            }
            case DESCONTO_FIXO -> {
                if (valor.signum() <= 0) {
                    throw new IllegalArgumentException("Desconto fixo deve ser maior que zero");
                }
                if (valor.compareTo(produto.getPreco()) > 0) {
                    throw new IllegalArgumentException("Desconto fixo não pode gerar preço negativo");
                }
            }
            case PRECO_FIXO -> {
                if (valor.signum() < 0) {
                    throw new IllegalArgumentException("Preço fixo não pode ser negativo");
                }
                if (valor.compareTo(produto.getPreco()) >= 0) {
                    throw new IllegalArgumentException("Preço promocional deve ser menor que o preço normal");
                }
            }
        }
    }

    private void validarPeriodo(Instant inicio, Instant fim) {
        if (!fim.isAfter(inicio)) {
            throw new IllegalArgumentException("Fim da promoção deve ser posterior ao início");
        }
    }

    private Promocao buscarEntidade(String id, String empresaId) {
        return promocaoRepository.findByIdPromocaoAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new NoSuchElementException("Promoção não encontrada"));
    }

    private Map<String, Set<String>> destinos(Promocao promocao) {
        Map<String, Set<String>> resultado = new LinkedHashMap<>();
        for (Produto produto : promocao.produtosAssociados()) {
            Set<String> condominios = promocao.getAbrangencia() == AbrangenciaPromocao.CONDOMINIO
                    ? Set.of(promocao.getCondominio().getIdCondominio())
                    : Set.copyOf(estoqueRepository.findActiveCondominiumIdsByProductId(produto.getIdProduto()));
            resultado.put(produto.getIdProduto(), condominios);
        }
        return resultado;
    }

    private Map<String, Set<String>> merge(Map<String, Set<String>> left, Map<String, Set<String>> right) {
        Map<String, Set<String>> merged = new LinkedHashMap<>();
        left.forEach((produto, condominios) -> merged.put(produto, new LinkedHashSet<>(condominios)));
        right.forEach((produto, condominios) -> merged
                .computeIfAbsent(produto, ignored -> new LinkedHashSet<>()).addAll(condominios));
        return merged;
    }

    private void notificar(Map<String, Set<String>> destinos, ProdutoCatalogChangeReason motivo) {
        LocalDateTime atualizadoEm = agora();
        destinos.forEach((produtoId, condominios) -> {
            if (!condominios.isEmpty()) {
                estoqueRepository.touchCatalog(condominios, Set.of(produtoId), atualizadoEm);
                eventPublisher.publishEvent(new ProdutoCatalogChangedEvent(produtoId, motivo, condominios));
            }
        });
    }

    private PromocaoResponse response(Promocao promocao) {
        return PromocaoResponse.from(promocao, agora());
    }

    private LocalDateTime agora() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
