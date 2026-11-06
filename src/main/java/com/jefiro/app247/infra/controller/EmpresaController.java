package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.dto.EmpresaRequest;
import com.jefiro.app247.domain.model.dto.EmpresaResponse;
import com.jefiro.app247.domain.model.dto.EmpresaBrandingRequest;
import com.jefiro.app247.domain.model.dto.EmpresaBrandingResponse;
import com.jefiro.app247.infra.service.EmpresaService;
import com.jefiro.app247.infra.exception.ApiBusinessException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
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

    /** Envia o logo (JPEG ou PNG). {@code variante=escuro} grava o logo para fundo escuro. */
    @PostMapping(value = "/me/branding/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EmpresaBrandingResponse> enviarLogo(
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "claro") String variante) {
        return ResponseEntity.ok(service.enviarLogo(file, escuro(variante)));
    }

    @DeleteMapping("/me/branding/logo")
    public ResponseEntity<EmpresaBrandingResponse> removerLogo(
            @RequestParam(defaultValue = "claro") String variante) {
        return ResponseEntity.ok(service.removerLogo(escuro(variante)));
    }

    private static boolean escuro(String variante) {
        return switch (variante.trim().toLowerCase()) {
            case "claro" -> false;
            case "escuro" -> true;
            default -> throw new ApiBusinessException(HttpStatus.BAD_REQUEST, "LOGO_VARIANT_INVALID",
                    "Variante do logo deve ser claro ou escuro");
        };
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> encerrar(@AuthenticationPrincipal User gestor) {
        service.encerrarDefinitivamente(gestor);
        return ResponseEntity.noContent().build();
    }
}
