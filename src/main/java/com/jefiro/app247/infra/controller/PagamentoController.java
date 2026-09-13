package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.dto.mercadopago.PreferenceReturn;
import com.jefiro.app247.infra.service.PagamentoService;
import com.jefiro.app247.domain.model.dto.PointPaymentResponse;
import com.jefiro.app247.domain.model.dto.PaymentStatusResponse;
import com.jefiro.app247.infra.service.PaymentReconciliationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController()
@RequestMapping("/pagamento")
public class PagamentoController {
    private static final Logger log = LoggerFactory.getLogger(PagamentoController.class);
    @Autowired
    PagamentoService service;
    @Autowired
    PaymentReconciliationService reconciliationService;

    @GetMapping("/terminal/{carrinho_id}")
    public ResponseEntity<?> getPagamento(@PathVariable String carrinho_id) {
        service.gerarCobranca(carrinho_id);
        return ResponseEntity.ok(true);
    }

    @PostMapping("/terminal/{carrinho_id}")
    public ResponseEntity<?> iniciarPagamentoPoint(@PathVariable String carrinho_id) {
        log.info("[PAYMENT-BACKEND] request recebido cartId={}", carrinho_id);
        PointPaymentResponse response = service.gerarCobranca(carrinho_id);
        log.info("[PAYMENT-BACKEND] respondendo Terminal orderId={} status={} mpStatus={}",
                response.orderId(), response.status(), response.mercadoPagoStatus());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/terminal/{terminalId}/ativo")
    public ResponseEntity<PaymentStatusResponse> recuperarPagamentoAtivo(
            @PathVariable String terminalId) {
        return reconciliationService.recoverActiveForTerminal(terminalId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
