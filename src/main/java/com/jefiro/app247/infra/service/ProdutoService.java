package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.Produto;
import com.jefiro.app247.domain.model.dto.CreateProductDTO;
import com.jefiro.app247.domain.model.enum_type.ProdutoCatalogChangeReason;
import com.jefiro.app247.domain.model.enum_type.ProdutoCategoria;
import com.jefiro.app247.domain.model.enum_type.UnidadeMedida;
import com.jefiro.app247.infra.event.ProdutoCatalogChangedEvent;
import com.jefiro.app247.infra.repository.EstoqueCondominioRepository;
import com.jefiro.app247.infra.repository.ProdutoRepository;
import com.jefiro.app247.infra.repository.ProdutoCodigoBarrasRepository;
import com.jefiro.app247.infra.repository.ProdutoFiscalRepository;
import com.jefiro.app247.infra.repository.PerfilTributarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.jefiro.app247.infra.exception.ApiBusinessException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import com.jefiro.app247.domain.model.dto.ProdutoCondominioDisponibilidadeResponse;

@Service
public class ProdutoService {

    @Autowired
    private ProdutoRepository produtoRepository;
    @Autowired
    private ProdutoCodigoBarrasRepository codigoBarrasRepository;
    @Autowired
    private ProdutoFiscalRepository produtoFiscalRepository;
    @Autowired
    private PerfilTributarioRepository perfilTributarioRepository;
    @Autowired
    private FileStorageService fileStorageService;
    @Autowired
    EmpresaService empresaService;
    @Autowired
    private EstoqueCondominioRepository estoqueRepository;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired private AuditLogService auditLogService;

    @Transactional
    public Produto salvar(CreateProductDTO produtoDTO, MultipartFile file) throws IOException {
        if (produtoDTO == null) {
            return null;
        }
        String empresaId = EmpresaContext.require();
        if (produtoDTO.codigoInternoEfetivo() == null || produtoDTO.codigoInternoEfetivo().isBlank()) {
            throw new IllegalArgumentException("O código interno do produto é obrigatório.");
        }
        String codigoInterno = produtoDTO.codigoInternoEfetivo().trim();
        if (produtoRepository.existsByCodigoInternoAndEmpresaId(codigoInterno, empresaId)) {
            throw new ApiBusinessException(HttpStatus.CONFLICT, "PRODUCT_CODE_ALREADY_EXISTS",
                    "Código interno já utilizado.");
        }
        validarCodigosBarras(produtoDTO, empresaId, null);
        String urlImagem = null;

        if (file != null && !file.isEmpty()) {
            urlImagem = fileStorageService.salvarArquivo(file);
        }

        Produto produto = new Produto(produtoDTO);
        produto.setCodigoInterno(codigoInterno);
        produto.setEmpresa(empresaService.getEmpresa(empresaId));
        adicionarCodigos(produto, produtoDTO);
        sincronizarFiscal(produto, produtoDTO, empresaId);
        produto.setFoto(urlImagem);
        Produto salvo = produtoRepository.saveAndFlush(produto);
        auditLogService.record(salvo.getEmpresa(), "PRODUCT_CREATED", "Produto", salvo.getIdProduto(),
                null, java.util.Map.of("codigoInterno", salvo.getCodigoInterno(), "nome", salvo.getNome()), java.util.Map.of());
        publicar(salvo.getIdProduto(), ProdutoCatalogChangeReason.PRODUCT_CREATED, Set.of());
        return salvo;
    }

    @Transactional
    public List<Produto> salvarList(List<CreateProductDTO> produtoList) {
        if (produtoList.isEmpty()) {
            throw new RuntimeException("a lista não pode esta vazia");
        }
        Empresa empresa = empresaService.getEmpresa(EmpresaContext.require());
        List<Produto> produto = produtoList.stream().map(dto -> {
            validarCodigosBarras(dto, empresa.getId(), null);
            Produto p = new Produto(dto); p.setEmpresa(empresa); adicionarCodigos(p, dto);
            sincronizarFiscal(p, dto, empresa.getId()); return p;
        }).toList();
        return produtoRepository.saveAll(produto);
    }

    @Transactional
    public Produto atualizar(String id, CreateProductDTO dto, MultipartFile file) throws IOException {
        String empresaId = EmpresaContext.require();
        Produto produto = produtoRepository.findByIdProdutoAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new ApiBusinessException(
                        HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Produto não encontrado."));

        if (dto == null) {
            throw new IllegalArgumentException("Dados inválidos");
        }


        if (dto.codigoInternoEfetivo() != null && !dto.codigoInternoEfetivo().trim().isEmpty()) {
            String codigo = dto.codigoInternoEfetivo().trim();
            if (produtoRepository.existsByCodigoInternoAndEmpresaIdAndIdProdutoNot(codigo, empresaId, id)) {
                throw new ApiBusinessException(HttpStatus.CONFLICT, "PRODUCT_CODE_ALREADY_EXISTS",
                        "Código interno já utilizado.");
            }
            produto.setCodigoInterno(codigo);
        }
        if (dto.codigosBarras() != null) sincronizarCodigos(produto, dto, empresaId);
        if (dto.fiscal() != null) sincronizarFiscal(produto, dto, empresaId);

        if (dto.nome() != null && !dto.nome().trim().isEmpty()) {
            produto.setNome(dto.nome().trim());
        }

        if (dto.descricao() != null && !dto.descricao().trim().isEmpty()) {
            produto.setDescricao(dto.descricao().trim());
        }

        if (dto.preco() != null && dto.preco().compareTo(BigDecimal.ZERO) > 0) {
            produto.setPreco(MoneyPolicy.persistence(dto.preco()));
        }

        if (dto.unidadeMedida() != null && !dto.unidadeMedida().trim().isEmpty()) {
            produto.setUnidadeMedida(UnidadeMedida.valueOf(dto.unidadeMedida().trim()));
        }

        if (dto.categoria() != null && !dto.categoria().trim().isEmpty()) {
            produto.setCategoria(ProdutoCategoria.valueOf(dto.categoria().trim()));
        }

        if (dto.peso() != null && dto.peso().compareTo(BigDecimal.ZERO) >= 0) {
            produto.setPeso(dto.peso());
        }

        if (dto.pesoTolerancia() != null && dto.pesoTolerancia().compareTo(BigDecimal.ZERO) >= 0) {
            produto.setPesoTolerancia(dto.pesoTolerancia());
        }
        if (dto.ativo() != null) produto.setStatus(dto.ativo());

        // =========================
        // IMAGEM
        // =========================
        if (file != null && !file.isEmpty()) {

//            if (produto.getFoto() != null) {
//                fileStorageService.deletarArquivo(produto.getFoto());
//            }

            String urlImagem = fileStorageService.salvarArquivo(file);
            produto.setFoto(urlImagem);
        }

        Set<String> condominios = Set.copyOf(
                estoqueRepository.findActiveCondominiumIdsByProductId(produto.getIdProduto()));
        produto.setUpdateAt(java.time.Instant.now());
        Produto salvo = produtoRepository.saveAndFlush(produto);
        auditLogService.record(salvo.getEmpresa(), "PRODUCT_UPDATED", "Produto", salvo.getIdProduto(),
                null, java.util.Map.of("codigoInterno", salvo.getCodigoInterno(), "nome", salvo.getNome()), java.util.Map.of());
        publicar(salvo.getIdProduto(), ProdutoCatalogChangeReason.PRODUCT_UPDATED, condominios);
        return salvo;
    }

    @Transactional
    public Produto alterarDisponibilidade(String id, boolean ativo) {
        String empresaId = EmpresaContext.require();
        Produto produto = produtoRepository.findByIdProdutoAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new ApiBusinessException(
                        HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Produto não encontrado."));
        if (produto.isStatus() == ativo) return produto;

        Set<String> condominios = Set.copyOf(
                estoqueRepository.findActiveCondominiumIdsByProductId(produto.getIdProduto()));
        produto.setStatus(ativo);
        Produto salvo = produtoRepository.saveAndFlush(produto);
        auditLogService.record(salvo.getEmpresa(), ativo ? "PRODUCT_ACTIVATED" : "PRODUCT_DEACTIVATED",
                "Produto", salvo.getIdProduto(), null, java.util.Map.of("ativo", ativo), java.util.Map.of());
        publicar(salvo.getIdProduto(), ativo
                ? ProdutoCatalogChangeReason.PRODUCT_ACTIVATED
                : ProdutoCatalogChangeReason.PRODUCT_DEACTIVATED, condominios);
        return salvo;
    }

    public Page<Produto> listar(Pageable pageable) {
        return produtoRepository.findAllByEmpresaId(EmpresaContext.require(), pageable);
    }

    public Produto buscarPorCodigo(String codigo) {
        String empresaId = EmpresaContext.require();
        return codigoBarrasRepository.findByEmpresaIdAndCodigoBarrasAndAtivoTrue(empresaId, codigo)
                .map(com.jefiro.app247.domain.model.ProdutoCodigoBarras::getProduto)
                .or(() -> produtoRepository.findByCodigoInternoAndEmpresaId(codigo, empresaId))
                .orElseThrow(() -> new RuntimeException("Produto não encontrado"));
    }

    public com.jefiro.app247.domain.model.ProdutoCodigoBarras validarCodigoBarrasDoProduto(
            String empresaId, String produtoId, String codigo) {
        var barcode = codigoBarrasRepository
                .findByEmpresaIdAndCodigoBarrasAndAtivoTrue(empresaId, codigo)
                .orElseThrow(() -> new IllegalArgumentException("Código de barras não pertence ao catálogo da empresa"));
        if (!produtoId.equals(barcode.getProduto().getIdProduto())) {
            throw new IllegalArgumentException("Código de barras não pertence ao produto informado");
        }
        return barcode;
    }

    public Produto buscarPorId(String id) {
        return buscarPorIdDoTenant(id, EmpresaContext.require());
    }

    @Transactional(readOnly = true)
    public List<ProdutoCondominioDisponibilidadeResponse> disponibilidadeCondominios(String id) {
        String empresaId = EmpresaContext.require();
        buscarPorIdDoTenant(id, empresaId);
        return estoqueRepository.findProductAvailability(empresaId, id);
    }

    public Produto buscarPorIdDoTenant(String id, String empresaId) {
        return produtoRepository.findByIdProdutoAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new ApiBusinessException(
                        HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Produto não encontrado."));
    }

    public List<Produto> findTop10ByOrderByCreatedAtDesc() {
        return produtoRepository.findTop10ByEmpresaIdOrderByCreatedAtDesc(EmpresaContext.require());

    }

    private void publicar(String produtoId, ProdutoCatalogChangeReason motivo, Set<String> condominios) {
        eventPublisher.publishEvent(new ProdutoCatalogChangedEvent(produtoId, motivo, condominios));
    }

    private void validarCodigosBarras(CreateProductDTO dto, String empresaId, String produtoId) {
        java.util.HashSet<String> lote = new java.util.HashSet<>();
        long ativos = dto.codigosBarrasEfetivos().stream()
                .filter(CreateProductDTO.CodigoBarrasRequest::ativoEfetivo).count();
        long principais = dto.codigosBarrasEfetivos().stream()
                .filter(CreateProductDTO.CodigoBarrasRequest::ativoEfetivo)
                .filter(CreateProductDTO.CodigoBarrasRequest::principal).count();
        if (ativos > 0 && principais != 1) {
            throw new IllegalArgumentException("Informe exatamente um código de barras principal");
        }
        for (var codigo : dto.codigosBarrasEfetivos()) {
            String normalizado = codigo.codigo().trim();
            if (!lote.add(normalizado)) throw new IllegalArgumentException("Código de barras duplicado no produto");
            boolean existe = produtoId == null
                    ? codigoBarrasRepository.existsByEmpresaIdAndCodigoBarras(empresaId, normalizado)
                    : codigoBarrasRepository.existsByEmpresaIdAndCodigoBarrasAndProdutoIdProdutoNot(empresaId, normalizado, produtoId);
            if (existe) throw new ApiBusinessException(HttpStatus.CONFLICT,
                    "BARCODE_ALREADY_EXISTS",
                    "Código de barras já está associado a outro produto.");
        }
    }

    private void adicionarCodigos(Produto produto, CreateProductDTO dto) {
        dto.codigosBarrasEfetivos().forEach(c -> produto.adicionarCodigoBarras(
                c.codigo().trim(), c.tipo(), c.principal(), c.ativoEfetivo()));
    }

    private void sincronizarCodigos(Produto produto, CreateProductDTO dto, String empresaId) {
        validarCodigosBarras(dto, empresaId, produto.getIdProduto());
        java.util.Map<String, com.jefiro.app247.domain.model.ProdutoCodigoBarras> atuais =
                produto.getCodigosBarras().stream().collect(java.util.stream.Collectors.toMap(
                        com.jefiro.app247.domain.model.ProdutoCodigoBarras::getCodigoBarras, c -> c));
        java.util.Set<String> recebidos = new java.util.HashSet<>();
        for (var request : dto.codigosBarrasEfetivos()) {
            String codigo = request.codigo().trim(); recebidos.add(codigo);
            var entidade = atuais.get(codigo);
            if (entidade == null) {
                produto.adicionarCodigoBarras(
                        codigo, request.tipo(), request.principal(), request.ativoEfetivo());
            } else {
                entidade.setTipo(request.tipo()==null?"INTERNO":request.tipo());
                entidade.setAtivo(request.ativoEfetivo());
                entidade.setPrincipal(request.ativoEfetivo() && request.principal());
            }
        }
        atuais.forEach((codigo, entidade) -> { if (!recebidos.contains(codigo)) { entidade.setAtivo(false); entidade.setPrincipal(false); } });
    }

    private void sincronizarFiscal(Produto produto, CreateProductDTO dto, String empresaId) {
        var request = dto.fiscal();
        if (request == null) return;
        var fiscal = produto.getFiscal();
        if (fiscal == null && produto.getIdProduto() != null) {
            fiscal = produtoFiscalRepository.findByProdutoIdProdutoAndEmpresaId(
                    produto.getIdProduto(), empresaId).orElse(null);
        }
        if (fiscal == null) fiscal = new com.jefiro.app247.domain.model.ProdutoFiscal();
        fiscal.setProduto(produto);
        fiscal.setEmpresa(produto.getEmpresa());
        fiscal.setNcm(blankToNull(request.ncm()));
        fiscal.setCest(blankToNull(request.cest()));
        fiscal.setOrigemMercadoria(blankToNull(request.origemMercadoria()));
        fiscal.setUnidadeTributavel(blankToNull(request.unidadeTributavel()));
        fiscal.setFatorConversaoTributavel(request.fatorConversaoTributavel());
        fiscal.setGtinTributavel(blankToNull(request.gtinTributavel()));
        fiscal.setPerfilTributario(request.perfilTributarioId() == null
                || request.perfilTributarioId().isBlank() ? null
                : perfilTributarioRepository.findByIdAndEmpresaId(
                        request.perfilTributarioId(), empresaId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Perfil tributário não pertence à empresa atual")));
        produto.setFiscal(fiscal);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
