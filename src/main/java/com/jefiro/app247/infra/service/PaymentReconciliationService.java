package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.dto.OrderResponse;
import com.jefiro.app247.domain.model.dto.PaymentStatusResponse;
import com.jefiro.app247.domain.model.enum_type.order.OrderStatus;
import com.jefiro.app247.infra.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Service
public class PaymentReconciliationService {
    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationService.class);
    private static final List<OrderStatus> RECONCILIABLE = List.of(
            OrderStatus.PENDING, OrderStatus.CREATED, OrderStatus.AT_TERMINAL, OrderStatus.ACTION_REQUIRED);

    @Autowired OrderService orderService;
    @Autowired OrderRepository orderRepository;
    @Autowired MercadoPagoOrderQueryService mercadoPagoOrderQueryService;
    @Autowired PaymentStateTransitionService transitionService;
    @Autowired PointPaymentSubmissionService submissionService;

    @Value("${payment.reconciliation.window-hours:4}")
    int windowHours;
    @Value("${payment.reconciliation.batch-size:100}")
    int batchSize;
    @Value("${payment.reconciliation.submission-retry-cooldown-ms:5000}")
    long submissionRetryCooldownMs;

    public PaymentStatusResponse reconcileForTerminal(String orderId, String terminalId) {
        return reconcileForTerminal(orderId, terminalId, "STATUS_QUERY");
    }

    private PaymentStatusResponse reconcileForTerminal(
            String orderId, String terminalId, String origin) {
        Order order = orderService.getOrderForTerminal(orderId, terminalId);
        validateOwnership(order);
        if (!isReconciliable(order)) {
            return PaymentStatusResponse.from(order, false);
        }
        try {
            boolean remoteContacted = reconcileOrRecover(order, origin);
            return PaymentStatusResponse.from(
                    orderService.getOrderForTerminal(orderId, terminalId), remoteContacted);
        } catch (RuntimeException error) {
            log.warn("[PAYMENT-RECONCILIATION] consulta sob demanda falhou; origin={} orderId={} errorType={}",
                    origin, orderId, error.getClass().getSimpleName());
            return PaymentStatusResponse.from(orderService.getOrderForTerminal(orderId, terminalId), false);
        }
    }

    public boolean reconcileOrder(String orderId) {
        Order order = orderService.getOrderForReconciliation(orderId);
        return reconcileOrRecover(order, "MANUAL");
    }

    public Optional<PaymentStatusResponse> recoverActiveForTerminal(String terminalId) {
        List<String> orderIds = orderRepository.findUnresolvedPaymentOrderIdsForTerminal(
                terminalId, RECONCILIABLE, PageRequest.of(0, 2));
        if (orderIds.isEmpty()) {
            log.info("[PAYMENT-RECOVERY] nenhuma tentativa ativa terminalId={} origin=TERMINAL_STARTUP",
                    terminalId);
            return Optional.empty();
        }
        if (orderIds.size() > 1) {
            log.error("[PAYMENT-INTEGRITY] múltiplas orders não resolvidas terminalId={} orderIds={}",
                    terminalId, orderIds);
        }
        String orderId = orderIds.get(0);
        return Optional.of(reconcileForTerminal(orderId, terminalId, "TERMINAL_RECOVERY"));
    }

    public void reconcileRecent(String origin) {
        Instant cutoff = Instant.now().minus(java.time.Duration.ofHours(windowHours));
        List<String> candidateIds = orderRepository.findRecentReconciliationCandidateIds(
                RECONCILIABLE, cutoff, PageRequest.of(0, batchSize));
        log.info("[PAYMENT-RECONCILIATION] iniciando origin={} candidates={} windowHours={}",
                origin, candidateIds.size(), windowHours);
        for (String orderId : candidateIds) {
            try {
                Order order = orderService.getOrderForReconciliation(orderId);
                reconcileOrRecover(order, origin);
            } catch (RuntimeException error) {
                log.warn("[PAYMENT-RECONCILIATION] falha isolada; origin={} orderId={} errorType={}",
                        origin, orderId, error.getClass().getSimpleName());
            }
        }
    }

    private boolean reconcileOrRecover(Order order, String origin) {
        if (!isReconciliable(order)) return false;
        if (order.getMpOrderId() != null) {
            return reconcile(order, origin);
        }
        if (!isUnknownSubmissionEligible(order)) {
            log.info("[PAYMENT-RECOVERY] aguardando cooldown origin={} orderId={} attemptId={} status={}",
                    origin, order.getIdOrder(),
                    order.getPagamento() != null ? order.getPagamento().getIdPagamento() : null,
                    order.getStatus());
            return false;
        }
        log.warn("[PAYMENT-RECOVERY] repetindo mesma tentativa idempotente origin={} orderId={} attemptId={} status={}",
                origin, order.getIdOrder(), order.getPagamento().getIdPagamento(), order.getStatus());
        submissionService.submitSameAttempt(order.getIdOrder(), origin);
        return true;
    }

    private boolean isUnknownSubmissionEligible(Order order) {
        if (order.getStatus() != OrderStatus.PENDING || order.getPagamento() == null) {
            return false;
        }
        Instant lastChange = order.getPagamento().getUpdatedAt() != null
                ? order.getPagamento().getUpdatedAt()
                : order.getPagamento().getCreatedAt();
        return lastChange == null || !lastChange.plus(
                Duration.ofMillis(Math.max(0, submissionRetryCooldownMs))).isAfter(Instant.now());
    }

    private boolean reconcile(Order order, String origin) {
        validateOwnership(order);
        log.info("[PAYMENT-RECONCILIATION] origin={} orderId={} paymentId={} localStatus={} mpOrderId={}",
                origin,
                order.getIdOrder(),
                order.getPagamento() != null ? order.getPagamento().getIdPagamento() : null,
                order.getStatus(), order.getMpOrderId());
        OrderResponse remote = mercadoPagoOrderQueryService.getOrderByEmpresa(
                order.getEmpresa().getId(), order.getMpOrderId());
        if (remote.id() == null || !order.getMpOrderId().equals(remote.id())) {
            throw new IllegalStateException("Consulta retornou outra Order Mercado Pago");
        }
        if (remote.externalReference() == null
                || !order.getPagamento().getExternalReference().equals(remote.externalReference())) {
            throw new IllegalStateException("Consulta retornou external_reference divergente");
        }
        MercadoPagoOrderState state = MercadoPagoOrderState.from(remote);
        log.info("[PAYMENT-RECONCILIATION] orderId={} localStatus={} remoteStatus={}",
                order.getIdOrder(), order.getStatus(), state.status());
        transitionService.apply(state);
        return true;
    }

    private boolean isReconciliable(Order order) {
        return order != null && RECONCILIABLE.contains(order.getStatus());
    }

    private void validateOwnership(Order order) {
        if (order.getEmpresa() == null || order.getCarrinho() == null
                || order.getCarrinho().getTerminal() == null
                || order.getCarrinho().getTerminal().getCondominio() == null
                || order.getCarrinho().getTerminal().getCondominio().getEmpresa() == null
                || !order.getEmpresa().getId().equals(
                        order.getCarrinho().getTerminal().getCondominio().getEmpresa().getId())) {
            throw new IllegalStateException("Order, Terminal e Empresa possuem vínculo inconsistente");
        }
    }

}
