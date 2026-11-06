package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.MercadoPagoConta;
import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.PaymentAttempt;
import com.jefiro.app247.domain.model.terminal.Terminal;
import com.jefiro.app247.domain.model.dto.OrderResponse;
import com.jefiro.app247.domain.model.dto.MercadoPagoRefundResponse;
import com.jefiro.app247.domain.model.dto.mercadopago.OrderRequest;
import com.jefiro.app247.domain.model.enum_type.order.OrderStatus;
import com.jefiro.app247.infra.exception.ExternalServiceException;
import com.jefiro.app247.infra.exception.ExternalFailureType;
import com.jefiro.app247.infra.repository.TerminalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class MercadoPagoCobrancaService {

    private static final String DEFAULT_API_BASE_URL = "https://api.mercadopago.com";
    private static final Logger log = LoggerFactory.getLogger(MercadoPagoCobrancaService.class);

    @Value("${mercado-pago.api-base-url:https://api.mercadopago.com}")
    String apiBaseUrl;

    @Value("${mercado-pago.point.expiration-time:PT15M}")
    String expirationTime = "PT15M";

    @Autowired
    OauthMercadoPagoService oauthMercadoPagoService;
    @Autowired
    RestTemplate restTemplate;
    @Autowired
    OrderService orderService;
    @Autowired
    TerminalRepository terminalRepository;
    @Autowired
    MercadoPagoOperationalConfigurationService configurationService;

    public OrderResponse createRemoteOrder(String orderId) {
        return newOrder(orderService.getOrderForReconciliation(orderId));
    }

    public OrderResponse cancelRemoteOrder(Order order) {
        if (order == null || order.getPagamento() == null
                || order.getMpOrderId() == null || order.getMpOrderId().isBlank()) {
            throw new IllegalStateException("Order sem identidade remota para cancelamento");
        }
        PaymentAttempt attempt = order.getPagamento();
        if (attempt.getCancellationIdempotencyKey() == null
                || attempt.getCancellationIdempotencyKey().isBlank()) {
            throw new IllegalStateException("Cancelamento sem chave idempotente persistida");
        }
        String empresaId = order.getEmpresa().getId();
        Terminal terminal = terminalRepository
                .findByIdTerminalAndCondominioEmpresaId(order.getIdTerminal(), empresaId)
                .orElseThrow(() -> new IllegalStateException(
                        "Terminal interno não pertence à empresa da order"));
        MercadoPagoConta conta = configurationService.requireConfigured(terminal, empresaId);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(conta.getAccessToken());
        headers.set("X-Idempotency-Key", attempt.getCancellationIdempotencyKey());
        try {
            ResponseEntity<OrderResponse> response = restTemplate.exchange(
                    ordersUrl() + "/" + order.getMpOrderId() + "/cancel",
                    HttpMethod.POST, new HttpEntity<Void>(headers), OrderResponse.class);
            OrderResponse body = response.getBody();
            if (body == null || !order.getMpOrderId().equals(body.id())) {
                throw new IllegalStateException("Cancelamento retornou outra Order Mercado Pago");
            }
            if (body.externalReference() == null
                    || !attempt.getExternalReference().equals(body.externalReference())) {
                throw new IllegalStateException("Cancelamento retornou external_reference divergente");
            }
            return body;
        } catch (RestClientResponseException e) {
            ExternalFailureType failureType = classify(e);
            log.warn("Cancelamento Point não confirmado: orderId={}, httpStatus={}, failureType={}",
                    order.getIdOrder(), e.getStatusCode().value(), failureType);
            throw new ExternalServiceException("Mercado Pago", failureType,
                    "Cancelamento recusado com HTTP " + e.getStatusCode().value(), e);
        } catch (ResourceAccessException e) {
            throw new ExternalServiceException("Mercado Pago", ExternalFailureType.TIMEOUT,
                    "Timeout ao cancelar order", e);
        } catch (RestClientException e) {
            throw new ExternalServiceException("Mercado Pago", ExternalFailureType.UNAVAILABLE,
                    "Falha de comunicação ao cancelar order", e);
        }
    }

    public MercadoPagoRefundResponse refundRemoteOrder(Order order) {
        if (order == null || order.getPagamento() == null
                || order.getMpOrderId() == null || order.getMpOrderId().isBlank()) {
            throw new IllegalStateException("Order sem identidade remota para reembolso");
        }
        PaymentAttempt attempt = order.getPagamento();
        if (attempt.getRefundIdempotencyKey() == null
                || attempt.getRefundIdempotencyKey().isBlank()) {
            throw new IllegalStateException("Reembolso sem chave idempotente persistida");
        }
        String empresaId = order.getEmpresa().getId();
        Terminal terminal = terminalRepository
                .findByIdTerminalAndCondominioEmpresaId(order.getIdTerminal(), empresaId)
                .orElseThrow(() -> new IllegalStateException(
                        "Terminal interno não pertence à empresa da order"));
        MercadoPagoConta conta = configurationService.requireConfigured(terminal, empresaId);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(conta.getAccessToken());
        headers.set("X-Idempotency-Key", attempt.getRefundIdempotencyKey());
        try {
            ResponseEntity<MercadoPagoRefundResponse> response = restTemplate.exchange(
                    ordersUrl() + "/" + order.getMpOrderId() + "/refund",
                    HttpMethod.POST, new HttpEntity<Void>(headers), MercadoPagoRefundResponse.class);
            MercadoPagoRefundResponse body = response.getBody();
            if (body == null || !order.getMpOrderId().equals(body.id())) {
                throw new IllegalStateException("Reembolso retornou outra Order Mercado Pago");
            }
            if (!"refunded".equalsIgnoreCase(body.status())) {
                // Reembolso aceito mas ainda em processamento: confirmação vem por webhook/reconciliação.
                throw new ExternalServiceException("Mercado Pago", ExternalFailureType.UNKNOWN,
                        "Mercado Pago ainda não confirmou o reembolso (status=" + body.status() + ")", null);
            }
            return body;
        } catch (RestClientResponseException e) {
            ExternalFailureType failureType = classify(e);
            String responseBody = e.getResponseBodyAsString();
            log.warn("Reembolso Point não confirmado: orderId={}, httpStatus={}, failureType={}, body={}",
                    order.getIdOrder(), e.getStatusCode().value(), failureType,
                    responseBody.length() > 500 ? responseBody.substring(0, 500) : responseBody);
            throw new ExternalServiceException("Mercado Pago", failureType,
                    "Reembolso recusado com HTTP " + e.getStatusCode().value(), e);
        } catch (ResourceAccessException e) {
            throw new ExternalServiceException("Mercado Pago", ExternalFailureType.TIMEOUT,
                    "Timeout ao reembolsar order", e);
        } catch (RestClientException e) {
            throw new ExternalServiceException("Mercado Pago", ExternalFailureType.UNAVAILABLE,
                    "Falha de comunicação ao reembolsar order", e);
        }
    }

    OrderResponse newOrder(Order order) {
        log.info("[PAYMENT-BACKEND] chamando Mercado Pago orderId={}", order.getIdOrder());
        if (order.getPagamento() == null) {
            log.error("[PAYMENT-INTEGRITY] ORDER_WITHOUT_PAYMENT orderId={} mpOrderId={} status={}",
                    order.getIdOrder(), order.getMpOrderId(), order.getStatus());
            throw new IllegalStateException("Order sem Pagamento PENDING antes da chamada externa");
        }
        PaymentAttempt attempt = order.getPagamento();
        if (attempt.getIdPagamento() == null || attempt.getExternalReference() == null
                || attempt.getExternalReference().isBlank() || attempt.getIdempotencyKey() == null
                || attempt.getIdempotencyKey().isBlank() || attempt.getValor() == null
                || attempt.getValor().signum() <= 0) {
            throw new IllegalStateException("Tentativa de pagamento ainda não foi persistida");
        }
        if (order.getTotalCobrado() == null
                || attempt.getValor().compareTo(order.getTotalCobrado()) != 0) {
            throw new IllegalStateException(
                    "Valor da tentativa diverge do total financeiro congelado da Order");
        }
        if (order.getEmpresa() == null) {
            throw new IllegalStateException("Order sem empresa");
        }
        String empresaId = order.getEmpresa().getId();
        if (EmpresaContext.get() != null && !empresaId.equals(EmpresaContext.get())) {
            throw new IllegalStateException("Order não pertence à empresa autenticada");
        }
        if (order.getIdTerminal() == null || order.getIdTerminal().isBlank()) {
            throw new IllegalStateException("Order sem terminal interno");
        }

        Terminal terminal = terminalRepository
                .findByIdTerminalAndCondominioEmpresaId(order.getIdTerminal(), empresaId)
                .orElseThrow(() -> new IllegalStateException("Terminal interno não pertence à empresa da order"));
        MercadoPagoConta mercadoPagoConta = configurationService.requireConfigured(terminal, empresaId);

        OrderRequest request = new OrderRequest(
                "point",
                attempt.getExternalReference(),
                expirationTime,
                "Venda PDV",
                new OrderRequest.TransactionsRequest(
                        List.of(new OrderRequest.PaymentRequest(
                                MoneyPolicy.charged(attempt.getValor()).toPlainString()))
                ),
                new OrderRequest.ConfigRequest(
                        new OrderRequest.PointRequest(terminal.getMercadoPagoTerminalId(), "no_ticket")
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(mercadoPagoConta.getAccessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Idempotency-Key", attempt.getIdempotencyKey());

        HttpEntity<OrderRequest> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<OrderResponse> response = restTemplate.exchange(
                    ordersUrl(),
                    HttpMethod.POST,
                    entity,
                    OrderResponse.class);
            log.info("[PAYMENT-BACKEND] Mercado Pago respondeu orderId={} httpStatus={}",
                    order.getIdOrder(), response.getStatusCode().value());
            OrderResponse orderResponse = response.getBody();
            if (orderResponse == null || orderResponse.id() == null) {
                throw new IllegalStateException("Mercado Pago retornou criação de order sem corpo ou sem id");
            }
            if (orderResponse.externalReference() != null
                    && !orderResponse.externalReference().equals(attempt.getExternalReference())) {
                throw new IllegalStateException("Mercado Pago retornou external_reference divergente");
            }

            OrderStatus status = OrderStatus.findByValue(orderResponse.status());
            if (status == null) {
                // O ID remoto é mais importante que interpretar imediatamente
                // um status novo. O chamador persiste a identidade da Order e a
                // transição mantém o estado local conservador até reconciliação.
                log.warn("Criação Point respondeu com status desconhecido: orderId={}, mpOrderId={}, status={}",
                        order.getIdOrder(), orderResponse.id(), orderResponse.status());
            } else if (status != OrderStatus.CREATED && status != OrderStatus.AT_TERMINAL) {
                // A resposta ainda é autoritativa: o chamador precisa persistir o
                // remoteOrderId antes de aplicar inclusive um estado terminal.
                log.warn("Criação Point respondeu em estado não inicial: orderId={}, mpOrderId={}, status={}",
                        order.getIdOrder(), orderResponse.id(), status);
            }
            log.info("[PAYMENT-BACKEND] status MP={} orderId={} mpOrderId={}",
                    status, order.getIdOrder(), orderResponse.id());

            log.info("Cobrança Point enviada: orderId={}, mpOrderId={}, terminalId={}, status={}",
                    order.getIdOrder(), orderResponse.id(), order.getIdTerminal(), status);
            return orderResponse;

        } catch (RestClientResponseException e) {
            ExternalFailureType failureType = classify(e);
            log.warn("Criação Point recusada: orderId={}, terminalId={}, httpStatus={}, failureType={}",
                    order.getIdOrder(), order.getIdTerminal(), e.getStatusCode().value(), failureType);
            throw new ExternalServiceException("Mercado Pago", failureType,
                    "Criação de order recusada com HTTP " + e.getStatusCode().value(), e);
        } catch (ResourceAccessException e) {
            throw new ExternalServiceException("Mercado Pago", ExternalFailureType.TIMEOUT,
                    "Timeout ou indisponibilidade ao criar order", e);
        } catch (RestClientException e) {
            throw new ExternalServiceException("Mercado Pago", ExternalFailureType.UNAVAILABLE,
                    "Falha de comunicação ao criar order", e);
        }
    }

    private ExternalFailureType classify(RestClientResponseException exception) {
        String response = exception.getResponseBodyAsString().toLowerCase(java.util.Locale.ROOT);
        if (response.contains("already_queued_order_for_terminal")) {
            return ExternalFailureType.ACTIVE_CHARGE;
        }
        if (response.contains("idempotency_key_already_used")
                || response.contains("idempotency_validation_failed")) {
            return ExternalFailureType.IDEMPOTENCY_CONFLICT;
        }
        if (exception.getStatusCode().value() == 401) {
            return ExternalFailureType.AUTHENTICATION;
        }
        if (exception.getStatusCode().value() == 403
                || response.contains("forbidden_checking_terminal_owner")) {
            return ExternalFailureType.TERMINAL_NOT_FOUND;
        }
        if (exception.getStatusCode().is4xxClientError()) {
            return ExternalFailureType.INVALID_REQUEST;
        }
        if (exception.getStatusCode().is5xxServerError()) {
            return ExternalFailureType.UNAVAILABLE;
        }
        return ExternalFailureType.UNKNOWN;
    }

    private String ordersUrl() {
        String baseUrl = apiBaseUrl == null || apiBaseUrl.isBlank()
                ? DEFAULT_API_BASE_URL : apiBaseUrl;
        return baseUrl.replaceAll("/+$", "") + "/v1/orders";
    }
}
