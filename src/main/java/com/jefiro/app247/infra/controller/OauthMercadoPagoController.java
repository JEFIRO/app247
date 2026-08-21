package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.infra.dto.mercadopago.MercadoPagoTerminalResponse;
import com.jefiro.app247.infra.dto.mercadopago.MercadoPagoSetupStatusResponse;
import com.jefiro.app247.infra.service.MercadoPagoTerminalService;
import com.jefiro.app247.infra.service.MercadoPagoSetupStatusService;
import com.jefiro.app247.infra.service.OauthMercadoPagoService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
public class OauthMercadoPagoController {
    private final OauthMercadoPagoService oauthService;
    private final MercadoPagoTerminalService terminalService;
    private final MercadoPagoSetupStatusService setupStatusService;

    public OauthMercadoPagoController(OauthMercadoPagoService oauthService,
                                      MercadoPagoTerminalService terminalService,
                                      MercadoPagoSetupStatusService setupStatusService) {
        this.oauthService = oauthService;
        this.terminalService = terminalService;
        this.setupStatusService = setupStatusService;
    }

    @GetMapping("/mercado-pago/oauth")
    public void conectar(HttpServletResponse response, @AuthenticationPrincipal User gestor) throws IOException {
        response.sendRedirect(oauthService.url(gestor));
    }

    @Deprecated
    @GetMapping("/mp/oauth/mercadopago/{idUser}")
    public void conectarLegado(HttpServletResponse response, @PathVariable String idUser,
                               @AuthenticationPrincipal User gestor) throws IOException {
        response.sendRedirect(oauthService.url(gestor));
    }

    @GetMapping({"/mercado-pago/oauth/callback", "/mp/oauth/callback"})
    public ResponseEntity<Void> callback(@RequestParam String code, @RequestParam String state) {
        oauthService.gerarToken(code, state);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/mercado-pago/terminais")
    public ResponseEntity<List<MercadoPagoTerminalResponse>> listarTerminais() {
        return ResponseEntity.ok(terminalService.listar());
    }

    @GetMapping("/mercado-pago/status")
    public ResponseEntity<MercadoPagoSetupStatusResponse> consultarStatus() {
        return ResponseEntity.ok(setupStatusService.consultar());
    }

    @Deprecated
    @GetMapping("/mp/oauth/terminal/{idUser}")
    public ResponseEntity<List<MercadoPagoTerminalResponse>> listarTerminaisLegado(@PathVariable String idUser) {
        return ResponseEntity.ok(terminalService.listar());
    }
}
