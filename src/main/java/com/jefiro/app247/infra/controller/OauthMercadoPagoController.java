package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.MercadoPagoConta;
import com.jefiro.app247.domain.model.dto.mercadopago.TerminalResponse;
import com.jefiro.app247.infra.service.OauthMercadoPagoService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;


@RestController
@RequestMapping("/mp")
public class OauthMercadoPagoController {
    @Autowired
    private OauthMercadoPagoService service;

    @GetMapping("/oauth/mercadopago/{id}")
    public void conectar(HttpServletResponse response, @PathVariable String id) throws IOException {
        response.sendRedirect(service.url(id));
    }

    @GetMapping("/oauth/callback")
    public ResponseEntity<?> callback(@RequestParam String code, @RequestParam String state) {
        service.gerarToken(code, state);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/oauth/terminal/{idUser}")
    public ResponseEntity<?> listarTerminais(@PathVariable String idUser) {
        return ResponseEntity.ok().body(service.listarTerminais(idUser));
    }

    @PostMapping("/oauth/terminal/{idUser}")
    public ResponseEntity<?> setTerminal(@PathVariable String idUser, @RequestBody TerminalResponse response) {
        return ResponseEntity.ok().body(service.setTerminal(idUser, response));
    }
}
