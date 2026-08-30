package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.Produto;
import com.jefiro.app247.domain.model.dto.CreateProductDTO;
import com.jefiro.app247.domain.model.dto.ProdutoDisponibilidadeRequest;
import com.jefiro.app247.domain.model.dto.ProdutoResponse;
import com.jefiro.app247.domain.model.dto.ProdutoSyncResponse;
import com.jefiro.app247.domain.model.dto.response.PageResponse;
import com.jefiro.app247.infra.service.ProdutoService;
import com.jefiro.app247.infra.service.ProdutoSyncService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/produtos")
public class ProdutoController {

    @Autowired
    private ProdutoService produtoService;
    @Autowired
    private ProdutoSyncService produtoSyncService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProdutoResponse> salvar(
            @RequestPart("data") @Valid CreateProductDTO productDTO,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) throws IOException {

        return ResponseEntity.ok(new ProdutoResponse(produtoService.salvar(productDTO, file)));
    }

    @PostMapping("save-list")
    public ResponseEntity<List<ProdutoResponse>> saveProdutos(@RequestBody @Valid List<CreateProductDTO> productDTOS) {
        return ResponseEntity.ok(produtoService.salvarList(productDTOS).stream().map(ProdutoResponse::new).toList());
    }

    @GetMapping
    public ResponseEntity<PageResponse<ProdutoListagemDTO>> listar(Pageable pageable) {

        Page<Produto> produtos = produtoService.listar(pageable);

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
    public ResponseEntity<ProdutoResponse> buscarPorCodigo(@PathVariable String codigo) {
        return ResponseEntity.ok(new ProdutoResponse(produtoService.buscarPorCodigo(codigo)));
    }

    @GetMapping("/id")
    public ResponseEntity<ProdutoResponse> buscarPorid(@RequestParam String id) {
        return ResponseEntity.ok(new ProdutoResponse(produtoService.buscarPorId(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProdutoResponse> atualizarProduto(
            @PathVariable String id,
            @RequestPart("data") CreateProductDTO dto,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) throws IOException {

        Produto produtoAtualizado = produtoService.atualizar(id, dto, file);

        return ResponseEntity.ok(new ProdutoResponse(produtoAtualizado));
    }

    @PatchMapping("/{id}/disponibilidade")
    public ResponseEntity<ProdutoResponse> alterarDisponibilidade(
            @PathVariable String id,
            @RequestBody @Valid ProdutoDisponibilidadeRequest request) {
        return ResponseEntity.ok(new ProdutoResponse(
                produtoService.alterarDisponibilidade(id, request.ativo())));
    }

    @GetMapping("/sync")
    public ProdutoSyncResponse sync(
            @RequestParam String uuidTerminal,
            @RequestParam(required = false) Instant lastSync) {
        return produtoSyncService.sincronizar(uuidTerminal, Optional.ofNullable(lastSync));
    }

    @GetMapping("/home")
    public ResponseEntity<?> produtosHome() {

        var destaques = produtoService.findTop10ByOrderByCreatedAtDesc();

        return ResponseEntity.ok(Map.of(
                "destaques", destaques.stream().map(ProdutoResponse::new).toList()
        ));
    }
}
