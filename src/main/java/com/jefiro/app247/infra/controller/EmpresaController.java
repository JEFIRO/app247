package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.dto.EmpresaRequest;
import com.jefiro.app247.domain.model.dto.EmpresaResponse;
import com.jefiro.app247.domain.model.dto.EmpresaBrandingRequest;
import com.jefiro.app247.domain.model.dto.EmpresaBrandingResponse;
import com.jefiro.app247.infra.service.EmpresaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.jefiro.app247.domain.model.auth.User;

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

    @GetMapping("/me/branding")
    public ResponseEntity<EmpresaBrandingResponse> branding() {
        return ResponseEntity.ok(service.branding());
    }

    @PutMapping("/me/branding")
    public ResponseEntity<EmpresaBrandingResponse> atualizarBranding(
            @RequestBody @Valid EmpresaBrandingRequest request) {
        return ResponseEntity.ok(service.atualizarBranding(request));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> encerrar(@AuthenticationPrincipal User gestor) {
        service.encerrarDefinitivamente(gestor);
        return ResponseEntity.noContent().build();
    }
}
