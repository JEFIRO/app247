package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Carrinho;
import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.Pagamento;
import com.jefiro.app247.domain.model.dto.PagamentoResponse;
import com.jefiro.app247.domain.model.dto.mercadopago.PreferenceReturn;
import com.jefiro.app247.domain.model.enum_type.OrderStatus;
import com.jefiro.app247.domain.model.enum_type.OriginRequest;
import com.jefiro.app247.domain.model.enum_type.PagamentoTipo;
import com.jefiro.app247.infra.event.MercadoPagoCobrancaEvent;
import com.jefiro.app247.infra.repository.PagamentoRepository;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.payment.PaymentCreateRequest;
import com.mercadopago.client.payment.PaymentPayerRequest;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.payment.Payment;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Map;

@Service
public class PagamentoService {
    @Autowired
    OrderService orderService;
    @Autowired
    CarrinhoService carrinhoService;
    @Autowired
    PagamentoRepository pagamentoRepository;
    @Autowired
    MercadoPagoService mercadoPagoService;
    @Autowired
    ApplicationEventPublisher eventPublisher;

    public Boolean gerarCobranca(String carrinho_id) {
        try {
            Carrinho carrinho = carrinhoService.getById(carrinho_id);

            Order order = orderService.criarCobranca(carrinho);

            Pagamento pagamento = new Pagamento(order);
            pagamentoRepository.save(pagamento);

            eventPublisher.publishEvent(new MercadoPagoCobrancaEvent(order));

            return true;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public ResponseEntity<PreferenceReturn> gerarCheckout(String carrinho_id, String user_id) throws Exception {
        Carrinho carrinho = carrinhoService.getById(carrinho_id);

        Order order = orderService.createOrder(carrinho.getIdCarrinho(), user_id);
        order.setOriginRequest(OriginRequest.APP);
        return ResponseEntity.ok(mercadoPagoService.criarCheckout(order));
    }


    public void send() {
        mercadoPagoService.sendEvent();
    }

}
