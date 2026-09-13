package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.dto.estoque.ContagemInventarioRequest;
import com.jefiro.app247.domain.model.dto.estoque.InventarioRequest;
import com.jefiro.app247.domain.model.dto.estoque.InventarioResponse;
import com.jefiro.app247.domain.model.enum_type.InventarioStatus;
import com.jefiro.app247.domain.model.enum_type.TipoLocalEstoque;
import com.jefiro.app247.infra.service.InventarioService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/inventarios")
public class InventarioController {
    @Autowired private InventarioService service;

    @PostMapping
    public ResponseEntity<InventarioResponse> iniciar(@RequestBody @Valid InventarioRequest request) {
        return ResponseEntity.ok(service.iniciar(request));
    }

    @GetMapping
    public ResponseEntity<Page<InventarioResponse>> listar(
            @RequestParam(required = false) InventarioStatus status,
            @RequestParam(required = false) TipoLocalEstoque localTipo,
            Pageable pageable) {
        return ResponseEntity.ok(service.listar(status, localTipo, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InventarioResponse> buscar(@PathVariable String id) {
        return ResponseEntity.ok(service.buscar(id));
    }

    @PutMapping("/{id}/itens/{itemId}/contagem")
    public ResponseEntity<InventarioResponse> contar(@PathVariable String id, @PathVariable String itemId,
                                                      @RequestBody @Valid ContagemInventarioRequest request) {
        return ResponseEntity.ok(service.contar(id, itemId, request));
    }

    @PostMapping("/{id}/finalizar")
    public ResponseEntity<InventarioResponse> finalizar(@PathVariable String id) {
        return ResponseEntity.ok(service.finalizar(id));
    }

    @PostMapping("/{id}/cancelar")
    public ResponseEntity<InventarioResponse> cancelar(@PathVariable String id) {
        return ResponseEntity.ok(service.cancelar(id));
    }
}
