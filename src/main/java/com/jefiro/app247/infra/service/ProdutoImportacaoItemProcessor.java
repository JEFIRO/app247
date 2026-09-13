package com.jefiro.app247.infra.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jefiro.app247.domain.model.*;
import com.jefiro.app247.domain.model.dto.importacao.ImportacaoProdutoPayload;
import com.jefiro.app247.domain.model.enum_type.*;
import com.jefiro.app247.infra.event.ProdutoCatalogChangedEvent;
import com.jefiro.app247.infra.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

@Service
public class ProdutoImportacaoItemProcessor {
    @Autowired private ImportacaoProdutoItemRepository itemRepository;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private ProdutoCodigoBarrasRepository barcodeRepository;
    @Autowired private EstoqueEmpresaRepository estoqueRepository;
    @Autowired private MovimentacaoEstoqueRepository movimentoRepository;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuditLogService auditLogService;
    @Autowired private ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void importar(String itemId) throws Exception {
        ImportacaoProdutoItem item = itemRepository.findForUpdate(itemId)
                .orElseThrow(() -> new IllegalStateException("Linha de importação não encontrada"));
        if (item.getStatus() == ImportacaoProdutoItemStatus.IMPORTADO) return;
        if (item.getStatus() != ImportacaoProdutoItemStatus.VALIDO
                || !ProdutoPlanilhaService.PRODUTOS.equals(item.getAba())) return;
        ImportacaoProdutoPayload payload = objectMapper.readValue(
                item.getDadosNormalizados(), ImportacaoProdutoPayload.class);
        Empresa empresa = item.getImportacao().getEmpresa();
        String empresaId = empresa.getId();
        if (produtoRepository.existsByCodigoInternoAndEmpresaId(payload.codigoInterno(), empresaId)) {
            throw new IllegalStateException("codigo_interno passou a existir depois da validação");
        }
        for (var barcode : payload.codigosBarras()) {
            if (barcodeRepository.existsByEmpresaIdAndCodigoBarras(empresaId, barcode.codigo())) {
                throw new IllegalStateException("codigo_barras passou a existir depois da validação: " + barcode.codigo());
            }
        }

        Produto produto = new Produto();
        produto.setEmpresa(empresa);
        produto.setCodigoInterno(payload.codigoInterno());
        produto.setNome(payload.nome());
        produto.setDescricao(payload.descricao());
        produto.setPreco(MoneyPolicy.persistence(payload.precoVenda()));
        produto.setCategoria(ProdutoCategoria.valueOf(payload.categoria()));
        produto.setUnidadeMedida(UnidadeMedida.valueOf(payload.unidadeMedida()));
        produto.setPeso(payload.peso());
        produto.setPesoTolerancia(payload.pesoTolerancia());
        produto.setStatus(payload.ativo());
        for (var request : payload.codigosBarras()) {
            ProdutoCodigoBarras barcode = new ProdutoCodigoBarras();
            barcode.setEmpresa(empresa); barcode.setProduto(produto); barcode.setCodigoBarras(request.codigo());
            barcode.setTipo(request.tipo()); barcode.setPrincipal(request.principal()); barcode.setAtivo(request.ativo());
            produto.getCodigosBarras().add(barcode);
        }
        if (fiscalPreenchido(payload.fiscal())) {
            ProdutoFiscal fiscal = new ProdutoFiscal();
            fiscal.setEmpresa(empresa); fiscal.setProduto(produto); fiscal.setNcm(payload.fiscal().ncm());
            fiscal.setCest(payload.fiscal().cest()); fiscal.setOrigemMercadoria(payload.fiscal().origemMercadoria());
            fiscal.setGtinTributavel(payload.fiscal().gtinTributavel());
            fiscal.setUnidadeTributavel(payload.fiscal().unidadeTributavel());
            produto.setFiscal(fiscal);
        }
        produto = produtoRepository.saveAndFlush(produto);

        if (payload.estoqueInicial() != null) {
            EstoqueEmpresa estoque = new EstoqueEmpresa();
            estoque.setEmpresa(empresa); estoque.setProduto(produto);
            estoque.setQuantidade(BigDecimal.ZERO.setScale(3));
            estoque.setAtivo(payload.estoqueInicial().ativo());
            estoque = estoqueRepository.saveAndFlush(estoque);
            BigDecimal quantidade = payload.estoqueInicial().quantidade();
            if (quantidade.signum() != 0) {
                estoque.setQuantidade(quantidade);
                estoqueRepository.save(estoque);
                MovimentacaoEstoque movimento = new MovimentacaoEstoque();
                movimento.setEmpresa(empresa); movimento.setEstoqueEmpresa(estoque);
                movimento.setTipo(TipoMovimentacaoEstoque.CARGA_INICIAL_IMPORTACAO);
                movimento.setQuantidade(quantidade); movimento.setQuantidadeAnterior(BigDecimal.ZERO.setScale(3));
                movimento.setQuantidadePosterior(quantidade);
                movimento.setMotivo(payload.estoqueInicial().motivo() == null
                        ? "Carga inicial por importação" : payload.estoqueInicial().motivo());
                movimento.setCreatedBy(item.getImportacao().getCreatedBy());
                movimento.setChaveIdempotencia("IMPORTACAO:" + item.getImportacao().getId() + ":" + item.getId());
                movimentoRepository.save(movimento);
            }
        }
        item.setProduto(produto);
        item.setStatus(ImportacaoProdutoItemStatus.IMPORTADO);
        item.setMensagem("Importado com sucesso");
        item.setProcessedAt(Instant.now());
        itemRepository.save(item);
        auditLogService.record(empresa, "PRODUCT_IMPORTED", "Produto", produto.getIdProduto(), null,
                Map.of("codigoInterno", produto.getCodigoInterno(), "importacaoId", item.getImportacao().getId()), Map.of());
        eventPublisher.publishEvent(new ProdutoCatalogChangedEvent(
                produto.getIdProduto(), ProdutoCatalogChangeReason.PRODUCT_CREATED, Set.of()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void marcarErro(String itemId, Throwable error) {
        itemRepository.findById(itemId).ifPresent(item -> {
            if (item.getStatus() == ImportacaoProdutoItemStatus.IMPORTADO) return;
            item.setStatus(ImportacaoProdutoItemStatus.ERRO);
            String mensagem = error.getMessage() == null ? "Falha ao importar produto" : error.getMessage();
            item.setMensagem(mensagem.substring(0, Math.min(1000, mensagem.length())));
            item.setProcessedAt(Instant.now());
            itemRepository.save(item);
        });
    }

    private boolean fiscalPreenchido(ImportacaoProdutoPayload.Fiscal fiscal) {
        return fiscal != null && java.util.stream.Stream.of(fiscal.ncm(), fiscal.cest(), fiscal.origemMercadoria(),
                fiscal.gtinTributavel(), fiscal.unidadeTributavel()).anyMatch(v -> v != null && !v.isBlank());
    }
}
