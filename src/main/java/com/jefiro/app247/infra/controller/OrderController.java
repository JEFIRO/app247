package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.dto.OrderResponse;
import com.jefiro.app247.infra.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
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
    public OrderResponse order(@RequestParam String carrinho_id) {
        return new OrderResponse(service.createOrder(carrinho_id, null));
    }

    @GetMapping()
    public OrderResponse getOrder(@RequestParam String carrinho_id) {
        return new OrderResponse(service.getOrder(carrinho_id));
    }


}
