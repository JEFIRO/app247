package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Carrinho;
import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.dto.PagamentoResponse;
import com.jefiro.app247.domain.model.enum_type.OriginRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
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

    public PagamentoResponse gerarPix(String carrinho_id) {

        Carrinho carrinho = carrinhoService.getById(carrinho_id);

        Order order = orderService.createOrder(carrinho.getCarrinhoId(), null);
        order.setOriginRequest(OriginRequest.TERMINAL);
        return mercadoPagoService.criarPix(order);
    }

    public Map<String, Object> gerarCheckout(String carrinho_id, Long user_id) throws Exception {
        Carrinho carrinho = carrinhoService.getById(carrinho_id);

        Order order = orderService.createOrder(carrinho.getCarrinhoId(), user_id);
        order.setOriginRequest(OriginRequest.APP);
        return mercadoPagoService.criarCheckout(order);
    }


}
