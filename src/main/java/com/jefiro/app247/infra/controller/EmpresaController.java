package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.dto.EmpresaRequest;
import com.jefiro.app247.domain.model.dto.EmpresaResponse;
import com.jefiro.app247.infra.service.EmpresaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/empresas")
public class EmpresaController {
    private final EmpresaService service;

    public EmpresaController(EmpresaService service) {
        this.service = service;
    }

    @GetMapping("/{empresaId}")
    public ResponseEntity<EmpresaResponse> buscar(@PathVariable String empresaId) {
        return ResponseEntity.ok(service.getEmpresaDoContexto(empresaId));
    }

    @PutMapping("/{empresaId}")
    public ResponseEntity<EmpresaResponse> atualizar(@PathVariable String empresaId,
                                                      @RequestBody @Valid EmpresaRequest request) {
        return ResponseEntity.ok(service.atualizar(empresaId, request));
    }
}
