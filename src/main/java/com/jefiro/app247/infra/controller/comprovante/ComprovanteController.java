package com.jefiro.app247.infra.controller.comprovante;

import com.jefiro.app247.domain.model.dto.comprovante.ComprovanteDigitalResponse;
import com.jefiro.app247.domain.model.dto.comprovante.ComprovanteRequest;
import com.jefiro.app247.infra.service.comprovante.ComprovanteDigitalService;
import com.jefiro.app247.infra.service.comprovante.ComprovanteEnvioResponse;
import com.jefiro.app247.infra.service.comprovante.ComprovanteService;
import com.jefiro.app247.infra.service.TerminalRequestAuthorizationService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/comprovante")
public class ComprovanteController {
    private final ComprovanteService service;
    @Autowired private TerminalRequestAuthorizationService terminalAuthorization;
    @Autowired private ComprovanteDigitalService digital;

    @Autowired
    public ComprovanteController(ComprovanteService service) {
        this.service = service;
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Devolve os dados do comprovante de Order aprovada do Terminal",
            security = @SecurityRequirement(name = "terminalToken"))
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Dados do comprovante"),
            @ApiResponse(responseCode = "401", description = "Credencial inválida"),
            @ApiResponse(responseCode = "403", description = "Terminal diferente da credencial"),
            @ApiResponse(responseCode = "404", description = "Order inexistente para o Terminal"),
            @ApiResponse(responseCode = "422", description = "Order ainda não aprovada")})
    public ResponseEntity<ComprovanteDigitalResponse> dados(
            @RequestHeader(value = "X-Terminal-Token", required = false) String credential,
            @PathVariable String orderId,
            @RequestParam String terminalId) {
        terminalAuthorization.authorize(credential, terminalId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(digital.paraTerminal(orderId, terminalId));
    }

    @PostMapping
    @Operation(summary = "Envia comprovante de Order aprovada pertencente ao Terminal",
            security = @SecurityRequirement(name = "terminalToken"))
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Envio aceito ou já processado"),
            @ApiResponse(responseCode = "401", description = "Credencial inválida"),
            @ApiResponse(responseCode = "403", description = "Order de outro Terminal"),
            @ApiResponse(responseCode = "422", description = "Order ainda não aprovada")})
    public ResponseEntity<ComprovanteEnvioResponse> enviar(
            @RequestHeader(value = "X-Terminal-Token", required = false) String credential,
            @Valid @RequestBody ComprovanteRequest request) {
        terminalAuthorization.authorize(credential, request.terminalId());
        return ResponseEntity.ok(service.enviar(request));
    }
}
