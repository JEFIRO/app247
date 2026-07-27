package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.dto.mercadopago.PreferenceReturn;
import com.jefiro.app247.infra.service.PagamentoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController()
@RequestMapping("/pagamento")
public class PagamentoController {
    @Autowired
    PagamentoService service;

    @GetMapping("/terminal/{carrinho_id}")
    public ResponseEntity<?> getPagamento(@PathVariable String carrinho_id) {
        return ResponseEntity.ok(service.gerarCobranca(carrinho_id));
    }
}
