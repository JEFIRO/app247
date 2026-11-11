package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.dto.notafiscal.ChaveAcessoResponse;
import com.jefiro.app247.domain.model.dto.notafiscal.NotaFiscalEntradaResponse;
import com.jefiro.app247.domain.model.dto.notafiscal.NotaFiscalItemRequest;
import com.jefiro.app247.infra.service.NotaFiscalEntradaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/estoque-empresa/notas-fiscais")
public class NotaFiscalEntradaController {
    @Autowired private NotaFiscalEntradaService service;

    @GetMapping("/chave/{chave}")
    public ResponseEntity<ChaveAcessoResponse> consultarChave(@PathVariable String chave) {
        return ResponseEntity.ok(service.consultarChave(chave));
    }

    @PostMapping("/chave/{chave}/importar")
    public ResponseEntity<NotaFiscalEntradaResponse> importarPorChave(@PathVariable String chave) {
        return ResponseEntity.ok(service.importarPorChave(chave));
    }

    @PostMapping(value = "/xml", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<NotaFiscalEntradaResponse> importarXml(@RequestPart("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(service.importarXml(file));
    }

    @GetMapping("/{id}")
    public ResponseEntity<NotaFiscalEntradaResponse> buscar(@PathVariable String id) {
        return ResponseEntity.ok(service.buscar(id));
    }

    @PutMapping("/{id}/itens/{itemId}")
    public ResponseEntity<NotaFiscalEntradaResponse> ajustarItem(@PathVariable String id, @PathVariable String itemId,
                                                                 @RequestBody NotaFiscalItemRequest request) {
        return ResponseEntity.ok(service.ajustarItem(id, itemId, request));
    }

    @PostMapping("/{id}/confirmar")
    public ResponseEntity<NotaFiscalEntradaResponse> confirmar(@PathVariable String id) {
        return ResponseEntity.ok(service.confirmar(id));
    }
}
