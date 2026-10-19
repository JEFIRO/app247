package com.jefiro.app247.infra.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jefiro.app247.domain.model.dto.OrderResponse;
import com.jefiro.app247.domain.model.dto.mercadopago.OrderWebhookNotification;
import com.jefiro.app247.domain.model.dto.PointPaymentResponse;
import com.jefiro.app247.domain.model.PaymentAttempt;
import com.jefiro.app247.domain.model.enum_type.PaymentProvider;
import com.jefiro.app247.infra.repository.PagamentoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

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
    @Autowired
    PagamentoRepository pagamentoRepository;

    public PointPaymentResponse gerarCobranca(String carrinho_id) {
        return gerarCobranca(carrinho_id, null);
    }

    public PointPaymentResponse gerarCobranca(String carrinho_id, String terminalId) {
        log.info("[PAYMENT-BACKEND] iniciando tentativa cartId={}", carrinho_id);
        PointPaymentPersistenceService.PreparedAttempt attempt =
                terminalId == null ? pointPaymentPersistenceService.prepare(carrinho_id)
                        : pointPaymentPersistenceService.prepare(carrinho_id, terminalId);

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

    /**
     * A assinatura do Mercado Pago cobre só {@code data.id}, request-id e ts; o corpo não é
     * assinado e uma assinatura válida pode ser reenviada. Por isso o estado nunca vem do
     * corpo: a order é sempre consultada na API, preferindo a conta da Empresa dona da
     * tentativa local em vez do {@code user_id} informado no webhook.
     */
    private MercadoPagoOrderState normalize(OrderWebhookNotification notification) {
        if (notification == null || notification.data() == null
                || notification.data().id() == null || notification.data().id().isBlank()) {
            throw new IllegalArgumentException("Webhook de order sem data.id");
        }

        OrderWebhookNotification.Data webhookData = notification.data();
        String mercadoPagoOrderId = webhookData.id();
        PaymentAttempt attempt = pagamentoRepository.findByProviderOrderId(mercadoPagoOrderId)
                .or(() -> webhookData.externalReference() == null || webhookData.externalReference().isBlank()
                        ? Optional.empty()
                        : pagamentoRepository.findByProviderAndExternalReference(
                                PaymentProvider.MERCADO_PAGO, webhookData.externalReference()))
                .orElse(null);

        OrderResponse response = attempt != null && attempt.getEmpresa() != null
                ? mercadoPagoOrderQueryService.getOrderByEmpresa(attempt.getEmpresa().getId(), mercadoPagoOrderId)
                : mercadoPagoOrderQueryService.getOrder(notification.userId(), mercadoPagoOrderId);
        if (response == null || response.id() == null || !mercadoPagoOrderId.equals(response.id())) {
            throw new IllegalStateException("Consulta retornou outra Order Mercado Pago");
        }
        if (response.externalReference() == null || response.externalReference().isBlank()) {
            throw new IllegalStateException("Consulta da order não retornou external_reference");
        }
        if (attempt != null && attempt.getExternalReference() != null
                && !attempt.getExternalReference().equals(response.externalReference())) {
            throw new IllegalStateException("Consulta retornou external_reference divergente");
        }
        return MercadoPagoOrderState.from(response);
    }


}
