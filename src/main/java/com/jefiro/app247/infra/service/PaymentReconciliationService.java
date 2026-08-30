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

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PaymentReconciliationService {
    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationService.class);
    private static final List<OrderStatus> RECONCILIABLE = List.of(
            OrderStatus.PENDING, OrderStatus.CREATED, OrderStatus.AT_TERMINAL, OrderStatus.ACTION_REQUIRED);

    @Autowired OrderService orderService;
    @Autowired OrderRepository orderRepository;
    @Autowired MercadoPagoOrderQueryService mercadoPagoOrderQueryService;
    @Autowired PaymentStateTransitionService transitionService;

    @Value("${payment.reconciliation.window-hours:4}")
    int windowHours;
    @Value("${payment.reconciliation.batch-size:100}")
    int batchSize;

    public PaymentStatusResponse reconcileForTerminal(String orderId, String terminalId) {
        Order order = orderService.getOrderForTerminal(orderId, terminalId);
        validateOwnership(order);
        if (!isReconciliable(order) || order.getMpOrderId() == null) {
            return PaymentStatusResponse.from(order, false);
        }
        try {
            reconcile(order);
            return PaymentStatusResponse.from(orderService.getOrderForTerminal(orderId, terminalId), true);
        } catch (RuntimeException error) {
            log.warn("[PAYMENT-RECONCILIATION] consulta sob demanda falhou; orderId={} errorType={}",
                    orderId, error.getClass().getSimpleName());
            return PaymentStatusResponse.from(orderService.getOrderForTerminal(orderId, terminalId), false);
        }
    }

    public boolean reconcileOrder(String orderId) {
        Order order = orderService.getOrderForReconciliation(orderId);
        if (!isReconciliable(order) || order.getMpOrderId() == null) return false;
        return reconcile(order);
    }

    public void reconcileRecent(String origin) {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(windowHours);
        List<String> candidateIds = orderRepository.findRecentReconciliationCandidateIds(
                RECONCILIABLE, cutoff, PageRequest.of(0, batchSize));
        log.info("[PAYMENT-RECONCILIATION] iniciando origin={} candidates={} windowHours={}",
                origin, candidateIds.size(), windowHours);
        for (String orderId : candidateIds) {
            try {
                reconcileOrder(orderId);
            } catch (RuntimeException error) {
                log.warn("[PAYMENT-RECONCILIATION] falha isolada; origin={} orderId={} errorType={}",
                        origin, orderId, error.getClass().getSimpleName());
            }
        }
    }

    private boolean reconcile(Order order) {
        validateOwnership(order);
        log.info("[PAYMENT-RECONCILIATION] orderId={} paymentId={} localStatus={} mpOrderId={}",
                order.getIdOrder(),
                order.getPagamento() != null ? order.getPagamento().getIdPagamento() : null,
                order.getStatus(), order.getMpOrderId());
        OrderResponse remote = mercadoPagoOrderQueryService.getOrderByEmpresa(
                order.getEmpresa().getId(), order.getMpOrderId());
        if (remote.id() == null || !order.getMpOrderId().equals(remote.id())) {
            throw new IllegalStateException("Consulta retornou outra Order Mercado Pago");
        }
        if (remote.externalReference() == null
                || !order.getIdOrder().equals(remote.externalReference())) {
            throw new IllegalStateException("Consulta retornou external_reference divergente");
        }
        MercadoPagoOrderState state = from(remote);
        log.info("[PAYMENT-RECONCILIATION] orderId={} localStatus={} remoteStatus={}",
                order.getIdOrder(), order.getStatus(), state.status());
        return transitionService.apply(state);
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

    private MercadoPagoOrderState from(OrderResponse response) {
        OrderResponse.Payment payment = response.transactions() != null
                && response.transactions().payments() != null
                && !response.transactions().payments().isEmpty()
                ? response.transactions().payments().get(0) : null;
        return new MercadoPagoOrderState(
                response.externalReference(), response.status(), response.statusDetail(),
                payment != null && payment.statusDetail() != null
                        ? payment.statusDetail() : response.statusDetail(),
                payment != null ? payment.id() : null,
                payment != null && payment.paymentMethod() != null ? payment.paymentMethod().id() : null,
                payment != null && payment.paymentMethod() != null ? payment.paymentMethod().type() : null,
                payment != null && payment.paymentMethod() != null ? payment.paymentMethod().installments() : null,
                response.id(), response.version(), response.lastUpdatedDate());
    }
}
