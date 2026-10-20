package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.dto.TerminalActivationResponse;
import com.jefiro.app247.domain.model.dto.TerminalRequest;
import com.jefiro.app247.domain.model.dto.TerminalResponseDTO;
import com.jefiro.app247.infra.service.TerminalService;
import com.jefiro.app247.infra.service.MercadoPagoTerminalService;
import com.jefiro.app247.infra.service.TerminalLifecycleService;
import com.jefiro.app247.infra.service.TerminalRequestAuthorizationService;
import com.jefiro.app247.domain.model.dto.TerminalBootstrapResponse;
import com.jefiro.app247.domain.model.dto.TerminalCredentialResponse;
import com.jefiro.app247.domain.model.dto.TerminalPairingApproveRequest;
import com.jefiro.app247.domain.model.dto.TerminalPairingClaimRequest;
import com.jefiro.app247.domain.model.dto.TerminalPairingClaimResponse;
import com.jefiro.app247.domain.model.dto.TerminalPairingStartRequest;
import com.jefiro.app247.domain.model.dto.TerminalPairingStartResponse;
import com.jefiro.app247.infra.service.TerminalPairingService;
import jakarta.servlet.http.HttpServletRequest;
import com.jefiro.app247.infra.dto.mercadopago.VincularTerminalMercadoPagoRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class TerminalController {
    private final TerminalService service;
    private final MercadoPagoTerminalService mercadoPagoTerminalService;
    private final TerminalLifecycleService lifecycleService;
    @org.springframework.beans.factory.annotation.Autowired
    private TerminalRequestAuthorizationService terminalAuthorization;
    @org.springframework.beans.factory.annotation.Autowired
    private TerminalPairingService pairingService;

    public TerminalController(TerminalService service, MercadoPagoTerminalService mercadoPagoTerminalService,
                              TerminalLifecycleService lifecycleService) {
        this.service = service;
        this.mercadoPagoTerminalService = mercadoPagoTerminalService;
        this.lifecycleService = lifecycleService;
    }

    @GetMapping("/terminal/serial/{serial}")
    public ResponseEntity<TerminalActivationResponse> bySerial(@PathVariable String serial) {
        return ResponseEntity.ok(service.getBySerial(serial));
    }

    @PostMapping("/terminal/pairing")
    public ResponseEntity<TerminalPairingStartResponse> iniciarPareamento(
            @RequestBody @Valid TerminalPairingStartRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pairingService.start(request, httpRequest.getRemoteAddr()));
    }

    @PostMapping("/terminal/pairing/claim")
    public ResponseEntity<TerminalPairingClaimResponse> concluirPareamento(
            @RequestBody @Valid TerminalPairingClaimRequest request) {
        return ResponseEntity.ok(pairingService.claim(request.pairingToken()));
    }

    @PostMapping("/terminais/{terminalId}/pairing")
    public ResponseEntity<TerminalResponseDTO> parear(@PathVariable String terminalId,
                                                      @RequestBody @Valid TerminalPairingApproveRequest request) {
        return ResponseEntity.ok(pairingService.approve(terminalId, request.code()));
    }

    @PostMapping("/condominios/{condominioId}/terminais")
    public ResponseEntity<TerminalResponseDTO> criar(@PathVariable String condominioId,
                                                      @RequestBody @Valid TerminalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(condominioId, request));
    }

    @GetMapping("/condominios/{condominioId}/terminais")
    public ResponseEntity<List<TerminalResponseDTO>> listar(@PathVariable String condominioId) {
        return ResponseEntity.ok(service.listar(condominioId));
    }

    @GetMapping("/terminais")
    public ResponseEntity<List<TerminalResponseDTO>> listarTodos() {
        return ResponseEntity.ok(service.listarTodos());
    }

    @GetMapping("/terminais/{terminalId}")
    public ResponseEntity<TerminalResponseDTO> buscar(@PathVariable String terminalId) {
        return ResponseEntity.ok(service.buscar(terminalId));
    }

    @PutMapping("/terminais/{terminalId}")
    public ResponseEntity<TerminalResponseDTO> atualizar(@PathVariable String terminalId,
                                                          @RequestBody @Valid TerminalRequest request) {
        return ResponseEntity.ok(service.atualizar(terminalId, request));
    }

    @PutMapping("/terminais/{terminalId}/mercado-pago")
    public ResponseEntity<TerminalResponseDTO> vincularMercadoPago(
            @PathVariable String terminalId,
            @RequestBody @Valid VincularTerminalMercadoPagoRequest request) {
        return ResponseEntity.ok(mercadoPagoTerminalService.vincular(
                terminalId, request.mercadoPagoTerminalId()));
    }

    @DeleteMapping("/terminais/{terminalId}/mercado-pago")
    public ResponseEntity<Void> desvincularMercadoPago(@PathVariable String terminalId) {
        mercadoPagoTerminalService.desvincular(terminalId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/terminais/{terminalId}/credential")
    public ResponseEntity<TerminalCredentialResponse> provisionCredential(@PathVariable String terminalId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(terminalAuthorization.provision(terminalId));
    }

    @PostMapping("/terminais/{terminalId}/credential/rotate")
    public ResponseEntity<TerminalCredentialResponse> rotateCredential(@PathVariable String terminalId) {
        return ResponseEntity.ok(terminalAuthorization.rotate(terminalId));
    }

    @DeleteMapping("/terminais/{terminalId}/credential")
    public ResponseEntity<Void> revokeCredential(@PathVariable String terminalId) {
        terminalAuthorization.revoke(terminalId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/terminal/{terminalId}/bootstrap")
    public ResponseEntity<TerminalBootstrapResponse> bootstrap(
            @PathVariable String terminalId,
            @RequestHeader(value = "X-Terminal-Token", required = false) String credential) {
        terminalAuthorization.authorizeLifecycle(credential, terminalId);
        return ResponseEntity.ok(lifecycleService.consultar(terminalId));
    }

    @PostMapping("/terminal/{terminalId}/factory-reset/started")
    public ResponseEntity<Void> factoryResetStarted(
            @PathVariable String terminalId,
            @RequestHeader(value = "X-Terminal-Token", required = false) String credential) {
        terminalAuthorization.authorizeFactoryReset(credential, terminalId);
        lifecycleService.confirmarInicio(terminalId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/terminal/{terminalId}/factory-reset/completed")
    public ResponseEntity<Void> factoryResetCompleted(
            @PathVariable String terminalId,
            @RequestHeader(value = "X-Terminal-Token", required = false) String credential) {
        terminalAuthorization.authorizeFactoryReset(credential, terminalId);
        lifecycleService.confirmarConclusao(terminalId);
        return ResponseEntity.noContent().build();
    }
}
