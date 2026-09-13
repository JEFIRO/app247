package com.jefiro.app247.infra.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jefiro.app247.domain.model.dto.OrderResponse;
import com.jefiro.app247.domain.model.dto.mercadopago.OrderWebhookNotification;
import com.jefiro.app247.domain.model.dto.PointPaymentResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PagamentoService {
    private static final Logger log = LoggerFactory.getLogger(PagamentoService.class);
    @Autowired
    OrderService orderService;
    @Autowired
    CarrinhoService carrinhoService;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    MercadoPagoOrderQueryService mercadoPagoOrderQueryService;
    @Autowired
    PaymentStateTransitionService transitionService;
    @Autowired
    PaymentReconciliationService reconciliationService;
    @Autowired
    PointPaymentPersistenceService pointPaymentPersistenceService;
    @Autowired
    PointPaymentSubmissionService submissionService;

    public PointPaymentResponse gerarCobranca(String carrinho_id) {
        log.info("[PAYMENT-BACKEND] iniciando tentativa cartId={}", carrinho_id);
        PointPaymentPersistenceService.PreparedAttempt attempt =
                pointPaymentPersistenceService.prepare(carrinho_id);

        log.info("[PAYMENT] tentativa resolvida origin=TERMINAL_REQUEST orderId={} attemptId={} remoteOrderId={} submitOwner={}",
                attempt.orderId(), attempt.paymentAttemptId(), attempt.mercadoPagoOrderId(),
                attempt.shouldSubmit());

        if (!attempt.shouldSubmit()) {
            if (attempt.mercadoPagoOrderId() != null) {
                try {
                    reconciliationService.reconcileOrder(attempt.orderId());
                } catch (RuntimeException error) {
                    log.warn("[PAYMENT-RECONCILIATION] falha antes de reutilizar cobrança; orderId={} errorType={}",
                            attempt.orderId(), error.getClass().getSimpleName());
                }
            }
            return pointPaymentPersistenceService.current(attempt.orderId());
        }

        return submissionService.submitSameAttempt(attempt.orderId(), "TERMINAL_REQUEST");
    }

    @Transactional
    public void atualizarPagamento(String json) throws JsonProcessingException {
        OrderWebhookNotification notification = mapper.readValue(json, OrderWebhookNotification.class);
        transitionService.apply(normalize(notification));
    }

    private MercadoPagoOrderState normalize(OrderWebhookNotification notification) {
        if (notification == null || notification.data() == null) {
            throw new IllegalArgumentException("Webhook de order sem data");
        }

        OrderWebhookNotification.Data webhookData = notification.data();
        if (webhookData.externalReference() == null || webhookData.externalReference().isBlank()) {
            OrderResponse response = mercadoPagoOrderQueryService.getOrder(
                    notification.userId(),
                    webhookData.id()
            );
            if (response == null || response.externalReference() == null
                    || response.externalReference().isBlank()) {
                throw new IllegalStateException("Consulta da order não retornou external_reference");
            }
            OrderResponse.Payment payment = first(response.transactions() != null
                    ? response.transactions().payments() : null);
            return new MercadoPagoOrderState(
                    response.externalReference(),
                    response.status(),
                    response.statusDetail(),
                    payment != null && payment.statusDetail() != null
                            ? payment.statusDetail() : response.statusDetail(),
                    payment != null ? payment.id() : null,
                    payment != null && payment.paymentMethod() != null ? payment.paymentMethod().id() : null,
                    payment != null && payment.paymentMethod() != null ? payment.paymentMethod().type() : null,
                    payment != null && payment.paymentMethod() != null ? payment.paymentMethod().installments() : null,
                    response.id(),
                    response.version(),
                    response.lastUpdatedDate()
            );
        }

        OrderWebhookNotification.Payment payment = first(
                webhookData.transactions() != null ? webhookData.transactions().payments() : null
        );
        return new MercadoPagoOrderState(
                webhookData.externalReference(),
                webhookData.status(),
                webhookData.statusDetail(),
                payment != null && payment.statusDetail() != null
                        ? payment.statusDetail() : webhookData.statusDetail(),
                payment != null ? payment.id() : null,
                payment != null && payment.paymentMethod() != null ? payment.paymentMethod().id() : null,
                payment != null && payment.paymentMethod() != null ? payment.paymentMethod().type() : null,
                payment != null && payment.paymentMethod() != null ? payment.paymentMethod().installments() : null,
                webhookData.id(),
                webhookData.version(),
                notification.dateCreated()
        );
    }

    private <T> T first(List<T> values) {
        return values == null || values.isEmpty() ? null : values.get(0);
    }

}
