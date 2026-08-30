package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.dto.OrderDetailResponse;
import com.jefiro.app247.domain.model.dto.PaymentStatusResponse;
import com.jefiro.app247.infra.service.OrderService;
import com.jefiro.app247.infra.service.PaymentReconciliationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/order")
public class OrderController {

    @Autowired
    OrderService service;
    @Autowired
    PaymentReconciliationService reconciliationService;

    @GetMapping("/finalizar")
    public ResponseEntity<?> order(@RequestParam String carrinho_id) {
        return ResponseEntity.ok(new OrderDetailResponse(service.createOrder(carrinho_id, null)));
    }

    @GetMapping()
    public ResponseEntity<?> getOrder(@RequestParam String carrinho_id) {
        return ResponseEntity.ok(new OrderDetailResponse(service.getOrder(carrinho_id)));
    }

    @GetMapping("/{orderId}/status")
    public ResponseEntity<PaymentStatusResponse> getStatus(
            @PathVariable String orderId,
            @RequestParam String terminalId
    ) {
        return ResponseEntity.ok(reconciliationService.reconcileForTerminal(orderId, terminalId));
    }


}
