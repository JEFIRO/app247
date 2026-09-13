package com.jefiro.app247.infra.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jefiro.app247.domain.model.*;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.dto.importacao.ImportacaoProdutoPayload;
import com.jefiro.app247.domain.model.dto.importacao.ImportacaoProdutoResponse;
import com.jefiro.app247.domain.model.enum_type.*;
import com.jefiro.app247.infra.event.ImportacaoProdutoSolicitadaEvent;
import com.jefiro.app247.infra.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProdutoImportacaoService {
    @Autowired private ProdutoPlanilhaService planilhaService;
    @Autowired private ImportacaoProdutoRepository importacaoRepository;
    @Autowired private ImportacaoProdutoItemRepository itemRepository;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private ProdutoCodigoBarrasRepository barcodeRepository;
    @Autowired private EmpresaRepository empresaRepository;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuditLogService auditLogService;
    @Autowired private ApplicationEventPublisher eventPublisher;
    @Value("${product-import.max-file-bytes:10485760}") private long maxFileBytes;
    @Value("${product-import.max-rows:10000}") private int maxRows;

    public byte[] modelo() { return planilhaService.modelo(); }

    @Transactional
    public ImportacaoProdutoResponse validar(MultipartFile file) throws IOException {
        String empresaId = EmpresaContext.require();
        validarArquivo(file);
        byte[] bytes = file.getBytes();
        ProdutoPlanilhaService.Resultado resultado = planilhaService.ler(bytes, maxRows);
        Empresa empresa = empresaRepository.getReferenceById(empresaId);
        Map<String, ProdutoPlanilhaService.LinhaProduto> produtos = new LinkedHashMap<>();
        Map<String, List<String>> errosProduto = new HashMap<>();
        Map<String, Long> skuContagem = resultado.produtos().stream()
                .filter(p -> p.sku() != null && !p.sku().isBlank())
                .collect(Collectors.groupingBy(p -> chave(p.sku()), Collectors.counting()));
        for (var linha : resultado.produtos()) {
            String key = chave(linha.sku());
            if (key != null) produtos.putIfAbsent(key, linha);
            if (key != null && skuContagem.getOrDefault(key, 0L) > 1) linha.erros().add("codigo_interno duplicado no arquivo");
            if (linha.sku() != null && produtoRepository.existsByCodigoInternoAndEmpresaId(linha.sku(), empresaId)) {
                linha.erros().add("codigo_interno já existe na Empresa");
            }
            errosProduto.computeIfAbsent(key, ignored -> new ArrayList<>()).addAll(linha.erros());
        }

        Map<String, List<ProdutoPlanilhaService.LinhaBarcode>> barcodes = new HashMap<>();
        Set<String> codigosArquivo = new HashSet<>();
        Map<ProdutoPlanilhaService.LinhaBarcode, List<String>> errosBarcode = new LinkedHashMap<>();
        for (var linha : resultado.barcodes()) {
            List<String> erros = new ArrayList<>(linha.erros());
            String sku = chave(linha.sku());
            if (sku == null || !produtos.containsKey(sku)) erros.add("codigo_interno não existe na aba PRODUTOS");
            if (linha.codigo() != null && !codigosArquivo.add(chave(linha.codigo()))) erros.add("codigo_barras duplicado no arquivo");
            if (linha.codigo() != null && barcodeRepository.existsByEmpresaIdAndCodigoBarras(empresaId, linha.codigo())) {
                erros.add("codigo_barras já existe na Empresa");
            }
            errosBarcode.put(linha, erros);
            if (sku != null) {
                barcodes.computeIfAbsent(sku, ignored -> new ArrayList<>()).add(linha);
                errosProduto.computeIfAbsent(sku, ignored -> new ArrayList<>()).addAll(erros);
            }
        }
        for (Map.Entry<String, List<ProdutoPlanilhaService.LinhaBarcode>> entry : barcodes.entrySet()) {
            long principais = entry.getValue().stream().filter(b -> b.ativo() && b.principal()).count();
            if (principais != 1) {
                String erro = "informe exatamente um código de barras principal ativo";
                errosProduto.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>()).add(erro);
                entry.getValue().forEach(linha -> errosBarcode.get(linha).add(erro));
            }
        }

        Map<String, ProdutoPlanilhaService.LinhaEstoque> estoques = new HashMap<>();
        Set<String> estoqueSku = new HashSet<>();
        Map<ProdutoPlanilhaService.LinhaEstoque, List<String>> errosEstoque = new LinkedHashMap<>();
        for (var linha : resultado.estoques()) {
            List<String> erros = new ArrayList<>(linha.erros());
            String sku = chave(linha.sku());
            if (sku == null || !produtos.containsKey(sku)) erros.add("codigo_interno não existe na aba PRODUTOS");
            if (sku != null && !estoqueSku.add(sku)) erros.add("estoque inicial duplicado para o produto");
            errosEstoque.put(linha, erros);
            if (sku != null) {
                estoques.putIfAbsent(sku, linha);
                errosProduto.computeIfAbsent(sku, ignored -> new ArrayList<>()).addAll(erros);
            }
        }

        ImportacaoProduto importacao = new ImportacaoProduto();
        importacao.setEmpresa(empresa);
        importacao.setNomeArquivo(nomeSeguro(file.getOriginalFilename()));
        importacao.setHashArquivo(sha256(bytes));
        importacao.setModo(ModoImportacaoProduto.SOMENTE_NOVOS);
        importacao.setStatus(ImportacaoProdutoStatus.VALIDADA);
        importacao.setCreatedBy(usuarioAtual(empresaId));
        importacao.setTotalLinhas(resultado.totalLinhas());
        importacao = importacaoRepository.saveAndFlush(importacao);

        List<ImportacaoProdutoItem> itens = new ArrayList<>();
        for (var linha : resultado.produtos()) {
            String key = chave(linha.sku());
            List<String> erros = distintos(errosProduto.getOrDefault(key, linha.erros()));
            List<ImportacaoProdutoPayload.CodigoBarras> payloadBarcodes = barcodes.getOrDefault(key, List.of()).stream()
                    .map(b -> new ImportacaoProdutoPayload.CodigoBarras(b.codigo(), b.tipo(), b.principal(), b.ativo())).toList();
            var fiscal = new ImportacaoProdutoPayload.Fiscal(linha.ncm(), linha.cest(), linha.origem(),
                    linha.gtin(), linha.unidadeTributavel());
            var estoque = estoques.get(key);
            var payloadEstoque = estoque == null ? null : new ImportacaoProdutoPayload.EstoqueInicial(
                    estoque.quantidade(), estoque.motivo(), estoque.ativo());
            ImportacaoProdutoPayload payload = new ImportacaoProdutoPayload(linha.linha(), linha.sku(), linha.nome(),
                    linha.descricao(), linha.preco(), linha.categoria(), linha.unidade(), linha.peso(), linha.tolerancia(),
                    linha.ativo(), fiscal, payloadBarcodes, payloadEstoque);
            itens.add(item(importacao, ProdutoPlanilhaService.PRODUTOS, linha.linha(), linha.sku(), erros, json(payload)));
        }
        for (var entry : errosBarcode.entrySet()) {
            var linha = entry.getKey();
            itens.add(item(importacao, ProdutoPlanilhaService.BARCODES, linha.linha(), linha.sku(),
                    distintos(entry.getValue()), null));
        }
        for (var entry : errosEstoque.entrySet()) {
            var linha = entry.getKey();
            itens.add(item(importacao, ProdutoPlanilhaService.ESTOQUE, linha.linha(), linha.sku(),
                    distintos(entry.getValue()), null));
        }
        itemRepository.saveAll(itens);
        int totalErros = (int) itens.stream().filter(i -> i.getStatus() == ImportacaoProdutoItemStatus.ERRO).count();
        int totalValidas = (int) itens.stream().filter(i -> i.getAba().equals(ProdutoPlanilhaService.PRODUTOS)
                && i.getStatus() == ImportacaoProdutoItemStatus.VALIDO).count();
        importacao.setTotalErros(totalErros);
        importacao.setTotalValidas(totalValidas);
        importacao.setTotalAvisos(0);
        importacao.setStatus(totalErros == 0 ? ImportacaoProdutoStatus.VALIDADA : ImportacaoProdutoStatus.VALIDADA_COM_ERROS);
        importacao.setValidatedAt(Instant.now());
        importacaoRepository.saveAndFlush(importacao);
        auditLogService.record(empresa, "IMPORTACAO_PRODUTO_VALIDADA", "ImportacaoProduto", importacao.getId(), null,
                Map.of("arquivo", importacao.getNomeArquivo(), "validos", totalValidas, "erros", totalErros), Map.of());
        return ImportacaoProdutoResponse.from(importacao, itens);
    }

    @Transactional
    public ImportacaoProdutoResponse confirmar(String id) {
        String empresaId = EmpresaContext.require();
        ImportacaoProduto importacao = importacaoRepository.findForUpdate(id, empresaId)
                .orElseThrow(() -> new NoSuchElementException("Importação não encontrada"));
        if (importacao.getStatus() == ImportacaoProdutoStatus.PROCESSANDO
                || importacao.getStatus() == ImportacaoProdutoStatus.CONCLUIDA
                || importacao.getStatus() == ImportacaoProdutoStatus.CONCLUIDA_COM_ERROS) return resposta(importacao);
        if (importacao.getStatus() != ImportacaoProdutoStatus.VALIDADA || importacao.getTotalErros() > 0) {
            throw new IllegalStateException("Corrija os erros da planilha antes de confirmar a importação");
        }
        importacao.setStatus(ImportacaoProdutoStatus.PROCESSANDO);
        importacaoRepository.saveAndFlush(importacao);
        auditLogService.record(importacao.getEmpresa(), "IMPORTACAO_PRODUTO_CONFIRMADA", "ImportacaoProduto", id,
                Map.of("status", "VALIDADA"), Map.of("status", "PROCESSANDO"), Map.of());
        eventPublisher.publishEvent(new ImportacaoProdutoSolicitadaEvent(id));
        return resposta(importacao);
    }

    @Transactional(readOnly = true)
    public ImportacaoProdutoResponse buscar(String id) {
        ImportacaoProduto importacao = importacaoRepository.findByIdAndEmpresaId(id, EmpresaContext.require())
                .orElseThrow(() -> new NoSuchElementException("Importação não encontrada"));
        return resposta(importacao);
    }

    @Transactional(readOnly = true)
    public ImportacaoProdutoResponse erros(String id) {
        ImportacaoProduto importacao = importacaoRepository.findByIdAndEmpresaId(id, EmpresaContext.require())
                .orElseThrow(() -> new NoSuchElementException("Importação não encontrada"));
        return ImportacaoProdutoResponse.from(importacao,
                itemRepository.findAllByImportacaoIdAndStatusOrderByLinhaAsc(id, ImportacaoProdutoItemStatus.ERRO));
    }

    private ImportacaoProdutoResponse resposta(ImportacaoProduto importacao) {
        return ImportacaoProdutoResponse.from(importacao,
                itemRepository.findAllByImportacaoIdOrderByAbaAscLinhaAsc(importacao.getId()));
    }

    private ImportacaoProdutoItem item(ImportacaoProduto importacao, String aba, int linha, String sku,
                                        List<String> erros, String payload) {
        ImportacaoProdutoItem item = new ImportacaoProdutoItem();
        item.setEmpresa(importacao.getEmpresa()); item.setImportacao(importacao); item.setAba(aba);
        item.setLinha(linha); item.setCodigoInterno(sku); item.setDadosNormalizados(payload);
        item.setStatus(erros.isEmpty() ? ImportacaoProdutoItemStatus.VALIDO : ImportacaoProdutoItemStatus.ERRO);
        item.setMensagem(erros.isEmpty() ? "OK" : String.join("; ", erros));
        return item;
    }

    private void validarArquivo(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Selecione um arquivo XLSX");
        if (file.getSize() > maxFileBytes) throw new IllegalArgumentException("O arquivo excede o limite de 10 MB");
        String nome = nomeSeguro(file.getOriginalFilename()).toLowerCase(Locale.ROOT);
        if (!nome.endsWith(".xlsx")) throw new IllegalArgumentException("Formato não suportado. Envie um arquivo .xlsx");
        String mime = file.getContentType();
        if (mime != null && !mime.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                && !mime.equals("application/octet-stream")) throw new IllegalArgumentException("MIME do arquivo XLSX inválido");
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalArgumentException("Não foi possível normalizar a planilha", ex); }
    }
    private String nomeSeguro(String nome) {
        if (nome == null || nome.isBlank()) return "produtos.xlsx";
        return java.nio.file.Path.of(nome).getFileName().toString();
    }
    private String sha256(byte[] bytes) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception ex) { throw new IllegalStateException("SHA-256 indisponível", ex); }
    }
    private String chave(String value) { return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT); }
    private List<String> distintos(Collection<String> erros) { return erros.stream().filter(Objects::nonNull).distinct().toList(); }
    private User usuarioAtual(String empresaId) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user && user.getEmpresa() != null
                && empresaId.equals(user.getEmpresa().getId())) return user;
        return null;
    }
}
