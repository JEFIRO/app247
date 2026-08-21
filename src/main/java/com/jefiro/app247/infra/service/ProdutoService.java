package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.GrupoTributario;
import com.jefiro.app247.domain.model.Produto;
import com.jefiro.app247.domain.model.dto.CreateProductDTO;
import com.jefiro.app247.domain.model.enum_type.ProdutoCategoria;
import com.jefiro.app247.domain.model.enum_type.UnidadeMedida;
import com.jefiro.app247.infra.repository.ProdutoRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ProdutoService {

    @Autowired
    private ProdutoRepository produtoRepository;
    @Autowired
    private FileStorageService fileStorageService;
    @Autowired
    EmpresaService empresaService;

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
        return produtoRepository.save(produto);
    }

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
            produto.setPreco(dto.preco());
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

        return produtoRepository.save(produto);
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

    public List<Produto> sync(String lastSync) {
        try {
            var data = LocalDateTime.parse(lastSync);
            return produtoRepository.findAllByUpdateAtAfterAndEmpresaId(data, EmpresaContext.require());
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException("lastSync deve estar no formato ISO-8601", e);
        }
    }

    public List<Produto> findTop10ByOrderByCreatedAtDesc() {
        return produtoRepository.findTop10ByEmpresaIdOrderByCreateAtDesc(EmpresaContext.require());

    }
}
