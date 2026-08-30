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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Service
public class ProdutoService {

    @Autowired
    private ProdutoRepository produtoRepository;
    @Autowired
    private FileStorageService fileStorageService;
    @Autowired
    EmpresaService empresaService;
    @Autowired
    private EstoqueCondominioRepository estoqueRepository;
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Transactional
    public Produto salvar(CreateProductDTO produtoDTO, MultipartFile file) throws IOException {
        if (produtoDTO == null) {
            return null;
        }
        String empresaId = EmpresaContext.require();
        String codigo = produtoDTO.codigo().trim();
        if (produtoRepository.existsByCodigoAndEmpresaId(codigo, empresaId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este código de produto já está em uso.");
        }
        String urlImagem = null;

        if (file != null && !file.isEmpty()) {
            urlImagem = fileStorageService.salvarArquivo(file);
        }

        Produto produto = new Produto(produtoDTO);
        produto.setCodigo(codigo);
        produto.setEmpresa(empresaService.getEmpresa(empresaId));
        produto.setFoto(urlImagem);
        Produto salvo = produtoRepository.saveAndFlush(produto);
        publicar(salvo.getIdProduto(), ProdutoCatalogChangeReason.PRODUCT_CREATED, Set.of());
        return salvo;
    }

    @Transactional
    public List<Produto> salvarList(List<CreateProductDTO> produtoList) {
        if (produtoList.isEmpty()) {
            throw new RuntimeException("a lista não pode esta vazia");
        }
        Empresa empresa = empresaService.getEmpresa(EmpresaContext.require());
        List<Produto> produto = produtoList.stream().map(Produto::new).toList();
        produto.forEach(p ->
                p.setEmpresa(empresa)
        );
        return produtoRepository.saveAll(produto);
    }

    @Transactional
    public Produto atualizar(String id, CreateProductDTO dto, MultipartFile file) throws IOException {
        String empresaId = EmpresaContext.require();
        Produto produto = produtoRepository.findByIdProdutoAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado"));

        if (dto == null) {
            throw new IllegalArgumentException("Dados inválidos");
        }


        if (dto.codigo() != null && !dto.codigo().trim().isEmpty()) {
            String codigo = dto.codigo().trim();
            if (produtoRepository.existsByCodigoAndEmpresaIdAndIdProdutoNot(codigo, empresaId, id)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Este código de produto já está em uso.");
            }
            produto.setCodigo(codigo);
        }

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
        Produto salvo = produtoRepository.saveAndFlush(produto);
        publicar(salvo.getIdProduto(), ProdutoCatalogChangeReason.PRODUCT_UPDATED, condominios);
        return salvo;
    }

    @Transactional
    public Produto alterarDisponibilidade(String id, boolean ativo) {
        String empresaId = EmpresaContext.require();
        Produto produto = produtoRepository.findByIdProdutoAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado"));
        if (produto.isStatus() == ativo) return produto;

        Set<String> condominios = Set.copyOf(
                estoqueRepository.findActiveCondominiumIdsByProductId(produto.getIdProduto()));
        produto.setStatus(ativo);
        Produto salvo = produtoRepository.saveAndFlush(produto);
        publicar(salvo.getIdProduto(), ativo
                ? ProdutoCatalogChangeReason.PRODUCT_ACTIVATED
                : ProdutoCatalogChangeReason.PRODUCT_DEACTIVATED, condominios);
        return salvo;
    }

    public Page<Produto> listar(Pageable pageable) {
        return produtoRepository.findAllByEmpresaId(EmpresaContext.require(), pageable);
    }

    public Produto buscarPorCodigo(String codigo) {
        return produtoRepository.findByCodigoAndEmpresaId(codigo, EmpresaContext.require())
                .orElseThrow(() -> new RuntimeException("Produto não encontrado"));
    }

    public Produto buscarPorId(String id) {
        return buscarPorIdDoTenant(id, EmpresaContext.require());
    }

    public Produto buscarPorIdDoTenant(String id, String empresaId) {
        return produtoRepository.findByIdProdutoAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new RuntimeException("Produto não existe"));
    }

    public List<Produto> findTop10ByOrderByCreatedAtDesc() {
        return produtoRepository.findTop10ByEmpresaIdOrderByCreateAtDesc(EmpresaContext.require());

    }

    private void publicar(String produtoId, ProdutoCatalogChangeReason motivo, Set<String> condominios) {
        eventPublisher.publishEvent(new ProdutoCatalogChangedEvent(produtoId, motivo, condominios));
    }
}
