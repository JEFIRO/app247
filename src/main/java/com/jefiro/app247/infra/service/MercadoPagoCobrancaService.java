package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.MercadoPagoConta;
import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.Pagamento;
import com.jefiro.app247.domain.model.terminal.Terminal;
import com.jefiro.app247.domain.model.dto.OrderResponse;
import com.jefiro.app247.domain.model.dto.mercadopago.OrderRequest;
import com.jefiro.app247.domain.model.enum_type.order.OrderStatus;
import com.jefiro.app247.domain.model.enum_type.order.StatusDetail;
import com.jefiro.app247.infra.event.MercadoPagoCobrancaEvent;
import com.jefiro.app247.infra.exception.ExternalServiceException;
import com.jefiro.app247.infra.exception.ExternalFailureType;
import com.jefiro.app247.infra.repository.TerminalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.math.RoundingMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class MercadoPagoCobrancaService {

    private static final String URL = "https://api.mercadopago.com/v1/orders";
    private static final Logger log = LoggerFactory.getLogger(MercadoPagoCobrancaService.class);

    @Value("${mercado-pago.point.expiration-time:PT15M}")
    String expirationTime = "PT15M";

    @Autowired
    OauthMercadoPagoService oauthMercadoPagoService;
    @Autowired
    RestTemplate restTemplate;
    @Autowired
    OrderService orderService;
    @Autowired
    PagamentoService pagamentoService;
    @Autowired
    TerminalRepository terminalRepository;

    @EventListener
    public void ouvinte(MercadoPagoCobrancaEvent event) {
        newOrder(event.getOrder());
    }

    void newOrder(Order order) {
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
        if (terminal.getMercadoPagoTerminalId() == null || terminal.getMercadoPagoTerminalId().isBlank()) {
            throw new IllegalStateException("Terminal interno não possui maquininha Mercado Pago vinculada");
        }

        MercadoPagoConta mercadoPagoConta = oauthMercadoPagoService.getByEmpresa(empresaId);

        OrderRequest request = new OrderRequest(
                "point",
                order.getIdOrder(),
                expirationTime,
                "Venda PDV",
                new OrderRequest.TransactionsRequest(
                        List.of(new OrderRequest.PaymentRequest(
                                order.getTotal().setScale(2, RoundingMode.HALF_UP).toPlainString()))
                ),
                new OrderRequest.ConfigRequest(
                        new OrderRequest.PointRequest(terminal.getMercadoPagoTerminalId(), "no_ticket")
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(mercadoPagoConta.getAccessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Idempotency-Key", order.getIdOrder());

        HttpEntity<OrderRequest> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<OrderResponse> response = restTemplate.exchange(
                    URL,
                    HttpMethod.POST,
                    entity,
                    OrderResponse.class);
            OrderResponse orderResponse = response.getBody();
            if (orderResponse == null || orderResponse.id() == null) {
                throw new IllegalStateException("Mercado Pago retornou criação de order sem corpo ou sem id");
            }
            if (orderResponse.externalReference() != null
                    && !order.getIdOrder().equals(orderResponse.externalReference())) {
                throw new IllegalStateException("Mercado Pago retornou external_reference divergente");
            }

            OrderStatus status = OrderStatus.findByValue(orderResponse.status());
            if (status == null) {
                throw new IllegalStateException(
                        "Mercado Pago retornou status de order desconhecido: " + orderResponse.status()
                );
            }
            if (status != OrderStatus.CREATED && status != OrderStatus.AT_TERMINAL) {
                throw new IllegalStateException(
                        "Criação Point retornou estado não inicial: " + orderResponse.status());
            }

            // 1. Atualiza dados da Order
            order.setMpOrderId(orderResponse.id());
            order.setMpType(orderResponse.type());
            order.setStatus(status);
            order.setMpUserId(orderResponse.userId());
            order.setMpStatus(status);
            order.setMpStatusDetail(StatusDetail.findByValue(orderResponse.statusDetail()));
            if (orderResponse.config() != null && orderResponse.config().point() != null) {
                order.setMpTerminalId(orderResponse.config().point().terminalId());
            }
            // 2. Instancia e garante preenchimento de FKs obrigatórias do Pagamento
            Pagamento pagamento = new Pagamento(order, orderResponse);
            pagamento.setEmpresa(order.getEmpresa()); // <--- CRUCIAL: empresa_id não pode ser NULL no DB
            pagamento.setOrder(order);                // <--- Vincula ao pedido

            // 3. Persiste via Repository diretamente com saveAndFlush
            pagamento = pagamentoService.save(pagamento);

            // 4. Vincula de volta na Order e atualiza
            order.setPagamento(pagamento);
            orderService.save(order);
            log.info("Cobrança Point enviada: orderId={}, mpOrderId={}, terminalId={}, status={}",
                    order.getIdOrder(), order.getMpOrderId(), order.getIdTerminal(), status);

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
}
