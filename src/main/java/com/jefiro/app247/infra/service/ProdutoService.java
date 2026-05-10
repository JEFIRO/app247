package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Produto;
import com.jefiro.app247.domain.model.dto.CreateProductDTO;
import com.jefiro.app247.infra.repository.ProdutoRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

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


    public Page<Produto> listar(Pageable pageable) {
        return produtoRepository.findAll(pageable);
    }

    public Produto buscarPorCodigo(String codigo) {
        return produtoRepository.findByCodigo(codigo);
    }

    public Produto buscarPorId(Long id) {
        return produtoRepository.findById(id).orElseThrow(() -> new RuntimeException("Produto não existe"));
    }

}
