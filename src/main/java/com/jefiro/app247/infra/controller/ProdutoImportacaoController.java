package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.dto.importacao.ImportacaoProdutoResponse;
import com.jefiro.app247.infra.service.ProdutoImportacaoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/produtos/importacao")
public class ProdutoImportacaoController {
    @Autowired private ProdutoImportacaoService service;

    @GetMapping("/modelo")
    public ResponseEntity<byte[]> modelo() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=app247-importacao-produtos.xlsx")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(service.modelo());
    }

    @PostMapping(value = "/validar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportacaoProdutoResponse> validar(@RequestPart("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(service.validar(file));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ImportacaoProdutoResponse> buscar(@PathVariable String id) {
        return ResponseEntity.ok(service.buscar(id));
    }

    @PostMapping("/{id}/confirmar")
    public ResponseEntity<ImportacaoProdutoResponse> confirmar(@PathVariable String id) {
        return ResponseEntity.accepted().body(service.confirmar(id));
    }

    @GetMapping("/{id}/erros")
    public ResponseEntity<ImportacaoProdutoResponse> erros(@PathVariable String id) {
        return ResponseEntity.ok(service.erros(id));
    }
}
