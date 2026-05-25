package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Carrinho;
import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.dto.PagamentoResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class PagamentoService {
    @Autowired
    OrderService orderService;
    @Autowired
    CarrinhoService carrinhoService;
    @Autowired
    MercadoPagoService mercadoPagoService;

    public PagamentoResponse gerarPix(String orderId) {

        Order order = orderService.getOrder(orderId);

        return mercadoPagoService.criarPix(order);
    }

    public Map<String, Object> gerarCheckout(String orderId, Long user_id) throws Exception {
        Carrinho carrinho = carrinhoService.getById(orderId);

        Order order = orderService.createOrder(carrinho.getCarrinhoId(), user_id);

        return mercadoPagoService.criarCheckout(order);
    }


}
