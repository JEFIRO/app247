package com.jefiro.app247.infra.service;

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

    public Produto salvar(CreateProductDTO produtoDTO, MultipartFile file) throws IOException {
        if (produtoDTO == null) {
            return null;
        }
        String urlImagem = null;

        if (file != null && !file.isEmpty()) {
            urlImagem = fileStorageService.salvarArquivo(file);
        }

        Produto produto = new Produto(produtoDTO);
        produto.setFoto(urlImagem);
        return produtoRepository.save(produto);
    }

    public Produto atualizar(Long id, CreateProductDTO dto, MultipartFile file) throws IOException {

        Produto produto = produtoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado"));

        if (dto == null) {
            throw new IllegalArgumentException("Dados inválidos");
        }


        if (dto.codigo() != null && !dto.codigo().trim().isEmpty()) {
            produto.setCodigo(dto.codigo().trim());
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

        if (dto.quantidade() != null && dto.quantidade() >= 0) {
            produto.setQuantidade(dto.quantidade());
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
        return produtoRepository.findAll(pageable);
    }

    public Produto buscarPorCodigo(String codigo) {
        return produtoRepository.findByCodigo(codigo).orElseThrow(() -> new RuntimeException("Produto não encontrado"));
    }

    public Produto buscarPorId(Long id) {
        return produtoRepository.findById(id).orElseThrow(() -> new RuntimeException("Produto não existe"));
    }

    public List<Produto> sync(String lastSync) {

        try {
            var data = LocalDateTime.parse(lastSync);
            return produtoRepository.findAllByUpdateAtAfter(data);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    public List<Produto> findTop10ByOrderByCreatedAtDesc() {
        return produtoRepository.findTop10ByOrderByCreateAtDesc();

    }
}
