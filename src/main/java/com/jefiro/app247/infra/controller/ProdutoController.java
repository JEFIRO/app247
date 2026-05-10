package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.Produto;
import com.jefiro.app247.domain.model.dto.CreateProductDTO;
import com.jefiro.app247.domain.model.dto.response.PageResponse;
import com.jefiro.app247.infra.service.ProdutoService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/produtos")
public class ProdutoController {

    @Autowired
    private ProdutoService produtoService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Produto> salvar(
            @RequestPart("data") @Valid CreateProductDTO productDTO,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) throws IOException {

        System.out.println(productDTO);

        return ResponseEntity.ok(produtoService.salvar(productDTO, file));
    }

    @GetMapping
    public ResponseEntity<PageResponse<ProdutoListagemDTO>> listar(Pageable pageable) {
        // Busca a página de entidades
        Page<Produto> produtos = produtoService.listar(pageable);

        // Mapeia cada Produto para ProdutoListagemDTO
        List<ProdutoListagemDTO> dtos = produtos.getContent()
                .stream()
                .map(ProdutoListagemDTO::new)
                .toList();

        // Monta sua resposta customizada
        PageResponse<ProdutoListagemDTO> response = new PageResponse<>(
                dtos,
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
