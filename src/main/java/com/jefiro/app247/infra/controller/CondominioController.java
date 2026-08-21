package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.dto.CondominioRequest;
import com.jefiro.app247.domain.model.dto.CondominioResponse;
import com.jefiro.app247.infra.service.CondominioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/condominios")
public class CondominioController {
    private final CondominioService service;

    public CondominioController(CondominioService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<CondominioResponse> criar(@RequestBody @Valid CondominioRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(request));
    }

    @GetMapping
    public ResponseEntity<List<CondominioResponse>> listar() {
        return ResponseEntity.ok(service.listar());
    }

    @GetMapping("/{condominioId}")
    public ResponseEntity<CondominioResponse> buscar(@PathVariable String condominioId) {
        return ResponseEntity.ok(service.buscar(condominioId));
    }

    @PutMapping("/{condominioId}")
    public ResponseEntity<CondominioResponse> atualizar(@PathVariable String condominioId,
                                                        @RequestBody @Valid CondominioRequest request) {
        return ResponseEntity.ok(service.atualizar(condominioId, request));
    }
}
