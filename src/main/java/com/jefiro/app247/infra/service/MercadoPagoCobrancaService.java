package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.MercadoPagoConta;
import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.dto.OrderResponse;
import com.jefiro.app247.domain.model.dto.mercadopago.*;
import com.jefiro.app247.infra.event.MercadoPagoCobrancaEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.UUID;

@Service
public class MercadoPagoCobrancaService {

    private static final String URL = "https://api.mercadopago.com/v1/orders";

    @Autowired
    private OauthMercadoPagoService oauthMercadoPagoService;

    @EventListener
    public void ouvinte(MercadoPagoCobrancaEvent event) {
        newOrder(event.getOrder());
    }


    private OrderResponse newOrder(Order order) {
        RestTemplate restTemplate = new RestTemplate();

        MercadoPagoConta mercadoPagoConta = oauthMercadoPagoService.getByEmpresa("58453aae-b976-45f2-a2c1-7cb7502ac5f3");


        OrderRequest request =
                new OrderRequest(
                        "point",
                        UUID.randomUUID().toString(),
                        "Venda PDV",
                        new TransactionsRequest(
                                List.of(
                                        new PaymentRequest(String.valueOf(order.getPagamento().getValor()))
                                )
                        ),
                        new ConfigRequest(
                                new PointRequest(mercadoPagoConta.getTerminalId(), "no_ticket")
                        )
                );

        HttpHeaders headers = new HttpHeaders();

        headers.setBearerAuth(mercadoPagoConta.getAccessToken());

        headers.setContentType(MediaType.APPLICATION_JSON);

        headers.set(
                "X-Idempotency-Key",
                order.getIdOrder()
        );

        HttpEntity<OrderRequest> entity =
                new HttpEntity<>(request, headers);

        try {

            ResponseEntity<OrderResponse> response =
                    restTemplate.exchange(
                            URL,
                            HttpMethod.POST,
                            entity,
                            OrderResponse.class);
            System.out.println(response.getBody());

            return response.getBody();

        } catch (HttpClientErrorException e) {

            System.out.println(e.getResponseBodyAsString());

            throw e;
        }
    }


}
