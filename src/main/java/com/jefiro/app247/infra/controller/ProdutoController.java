package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.Produto;
import com.jefiro.app247.domain.model.dto.CreateProductDTO;
import com.jefiro.app247.domain.model.dto.response.PageResponse;
import com.jefiro.app247.infra.service.ProdutoService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/produtos")
public class ProdutoController {

    @Autowired
    private ProdutoService produtoService;

    @PostMapping()
    public ResponseEntity<Produto> salvar(@RequestBody @Valid CreateProductDTO productDTO) {
        System.out.println(productDTO);
        return ResponseEntity.ok(produtoService.salvar(productDTO));
    }

    @GetMapping
    public ResponseEntity<PageResponse<Produto>> listar(Pageable pageable) {

        Page<Produto> produtos = produtoService.listar(pageable);

        PageResponse<Produto> response = new PageResponse<>(
                produtos.getContent(),
                produtos.getNumber(),
                produtos.getSize(),
                produtos.getTotalElements(),
                produtos.getTotalPages()
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{codigo}")
    public ResponseEntity<Produto> buscarPorCodigo(@PathVariable String codigo) {
        return ResponseEntity.ok(produtoService.buscarPorCodigo(codigo));
    }
}
