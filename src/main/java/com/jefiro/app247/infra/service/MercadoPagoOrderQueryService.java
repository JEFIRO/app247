package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.MercadoPagoConta;
import com.jefiro.app247.domain.model.dto.OrderResponse;
import com.jefiro.app247.infra.repository.OauthMercadoPagoRepository;
import com.jefiro.app247.infra.exception.ExternalServiceException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

@Service
public class MercadoPagoOrderQueryService {

    private static final String ORDERS_URL = "https://api.mercadopago.com/v1/orders/";

    private final OauthMercadoPagoRepository contaRepository;
    private final RestTemplate restTemplate;

    public MercadoPagoOrderQueryService(
            OauthMercadoPagoRepository contaRepository,
            RestTemplate restTemplate
    ) {
        this.contaRepository = contaRepository;
        this.restTemplate = restTemplate;
    }

    public OrderResponse getOrder(String mercadoPagoUserId, String mercadoPagoOrderId) {
        if (mercadoPagoUserId == null || mercadoPagoUserId.isBlank()) {
            throw new IllegalArgumentException("Webhook resumido sem user_id");
        }
        if (mercadoPagoOrderId == null || mercadoPagoOrderId.isBlank()) {
            throw new IllegalArgumentException("Webhook resumido sem data.id");
        }

        MercadoPagoConta conta = contaRepository.findByMpUserId(mercadoPagoUserId)
                .orElseThrow(() -> new IllegalStateException(
                        "Conta Mercado Pago não encontrada para user_id=" + mercadoPagoUserId
                ));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(conta.getAccessToken());

        try {
            return restTemplate.exchange(
                    ORDERS_URL + mercadoPagoOrderId,
                    HttpMethod.GET,
                    new HttpEntity<Void>(headers),
                    OrderResponse.class
            ).getBody();
        } catch (RestClientException e) {
            throw new ExternalServiceException("Mercado Pago",
                    "Falha ao consultar order", e);
        }
    }
}
