package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Produto;
import com.jefiro.app247.domain.model.dto.CreateProductDTO;
import com.jefiro.app247.infra.repository.ProdutoRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class ProdutoService {

    @Autowired
    private ProdutoRepository produtoRepository;

    public Produto salvar(CreateProductDTO produtoDTO) {
        if (produtoDTO == null) {
            return null;
        }

        Produto produto = new Produto(produtoDTO);
        return produtoRepository.save(produto);
    }


    public Page<Produto> listar(Pageable pageable) {
        return produtoRepository.findAll(pageable);
    }

    public Produto buscarPorCodigo(String codigo) {
        return produtoRepository.findByCodigo(codigo);
    }
}
