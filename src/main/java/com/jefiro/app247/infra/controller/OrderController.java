package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.dto.OrderResponse;
import com.jefiro.app247.infra.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/order")
public class OrderController {

    @Autowired
    OrderService service;

    @GetMapping("/finalizar")
    public ResponseEntity<?> order(@RequestParam String carrinho_id) {
        return ResponseEntity.ok(new OrderResponse(service.createOrder(carrinho_id, null)));
    }

    @GetMapping()
    public ResponseEntity<?> getOrder(@RequestParam String carrinho_id) {
        return ResponseEntity.ok(new OrderResponse(service.getOrder(carrinho_id)));
    }


}
