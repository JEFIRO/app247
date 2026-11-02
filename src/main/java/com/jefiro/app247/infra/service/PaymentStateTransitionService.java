package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.PaymentAttempt;
import com.jefiro.app247.domain.model.dto.PointPaymentResponse;
import com.jefiro.app247.domain.model.enum_type.CarrinhoStatus;
import com.jefiro.app247.domain.model.enum_type.PagamentoTipo;
import com.jefiro.app247.domain.model.enum_type.PaymentMethodId;
import com.jefiro.app247.domain.model.enum_type.PaymentProvider;
import com.jefiro.app247.domain.model.enum_type.PagamentoStatus;
import com.jefiro.app247.domain.model.enum_type.order.OrderStatus;
import com.jefiro.app247.domain.model.enum_type.order.StatusDetail;
import com.jefiro.app247.domain.model.mapper.MercadoPagoStatusMapper;
import com.jefiro.app247.infra.event.CompraCanceladaEvent;
import com.jefiro.app247.infra.event.OrderNotCompletedEvent;
import com.jefiro.app247.infra.event.OrderPaidEvent;
import com.jefiro.app247.infra.event.PaymentEvent;
import com.jefiro.app247.infra.exception.UnknownExternalStatusException;
import com.jefiro.app247.infra.repository.PagamentoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.NoSuchElementException;

@Service
public class PaymentStateTransitionService {
    private static final Logger log = LoggerFactory.getLogger(PaymentStateTransitionService.class);

    @Autowired OrderService orderService;
    @Autowired CarrinhoService carrinhoService;
    @Autowired PagamentoRepository pagamentoRepository;
    @Autowired com.jefiro.app247.infra.repository.PaymentEventRepository paymentEventRepository;
    @Autowired ApplicationEventPublisher eventPublisher;

    @Transactional
    public boolean apply(MercadoPagoOrderState data) {
        OrderStatus remoteStatus = OrderStatus.findByValue(data.status());
        if (remoteStatus == null) {
            throw new UnknownExternalStatusException("Status Mercado Pago desconhecido: " + data.status());
        }

        PaymentAttempt pagamento = pagamentoRepository.findForTransition(
                PaymentProvider.MERCADO_PAGO, data.externalReference()).orElse(null);
        if (pagamento == null) {
            log.error("[PAYMENT-INTEGRITY] ATTEMPT_NOT_FOUND externalReference={} mpOrderId={}",
                    data.externalReference(), data.mercadoPagoOrderId());
            throw new IllegalStateException("ATTEMPT_NOT_FOUND: webhook sem tentativa local persistida");
        }
        Order order = pagamento.getOrder();
        PaymentAttempt currentAttempt = order.getPagamento();
        boolean current = currentAttempt != null
                && currentAttempt.getIdPagamento().equals(pagamento.getIdPagamento());
        if (pagamento.getProviderOrderId() == null && data.mercadoPagoOrderId() != null) {
            pagamento.setProviderOrderId(data.mercadoPagoOrderId());
        }
        if (pagamento.getProviderOrderId() != null && data.mercadoPagoOrderId() != null
                && !pagamento.getProviderOrderId().equals(data.mercadoPagoOrderId())) {
            throw new IllegalStateException("Order Mercado Pago divergente da cobrança persistida");
        }
        if (isStale(pagamento, data.version())) {
            log.info("Estado Mercado Pago obsoleto ignorado: orderId={}, version={}, currentVersion={}",
                    order.getIdOrder(), data.version(), pagamento.getProviderEventVersion());
            return false;
        }
        String providerEventId = providerEventId(data);
        if (paymentEventRepository.existsByProviderAndProviderEventId(
                pagamento.getProvider(), providerEventId)) {
            log.info("Evento Mercado Pago duplicado ignorado: orderId={}, providerEventId={}",
                    order.getIdOrder(), providerEventId);
            return false;
        }

        PagamentoStatus mappedPaymentStatus = MercadoPagoStatusMapper.toPagamentoStatus(remoteStatus);
        if (!canApplyAttemptStatus(pagamento.getStatus(), mappedPaymentStatus)) {
            log.warn("Transição de tentativa inválida ignorada: attemptId={}, current={}, received={}",
                    pagamento.getIdPagamento(), pagamento.getStatus(), mappedPaymentStatus);
            return false;
        }
        boolean appliesToOrder = current || remoteStatus == OrderStatus.PROCESSED
                || remoteStatus == OrderStatus.REFUNDED;
        OrderStatus localStatus = order.getStatus();
        boolean stateChanged = appliesToOrder && localStatus != remoteStatus;
        boolean approvalWinsCancellationRace = remoteStatus == OrderStatus.PROCESSED
                && localStatus != OrderStatus.PROCESSED
                && localStatus != OrderStatus.REFUNDED;
        if (stateChanged && localStatus != null && !localStatus.canTransitionTo(remoteStatus)
                && !approvalWinsCancellationRace) {
            log.warn("Transição Mercado Pago inválida ignorada: orderId={}, current={}, received={}, version={}",
                    order.getIdOrder(), localStatus, remoteStatus, data.version());
            return false;
        }
        PagamentoStatus pagamentoAnterior = pagamento.getStatus();
        pagamento.setUpdatedAt(Instant.now());
        if (data.paymentId() != null) pagamento.setTransactionId(data.paymentId());
        pagamento.setStatus(mappedPaymentStatus);
        if (mappedPaymentStatus == PagamentoStatus.PROCESSED
                || mappedPaymentStatus == PagamentoStatus.FAILED
                || mappedPaymentStatus == PagamentoStatus.CANCELED
                || mappedPaymentStatus == PagamentoStatus.EXPIRED
                || mappedPaymentStatus == PagamentoStatus.REFUNDED) {
            pagamento.setFinishedAt(Instant.now());
        }

        Object domainEvent = null;
        switch (remoteStatus) {
            case PROCESSED -> {
                Instant paidAt = pagamento.getPaidAt() != null ? pagamento.getPaidAt() : Instant.now();
                pagamento.setPaidAt(paidAt);
                if (appliesToOrder) {
                    order.setPaidAt(paidAt);
                    if (order.getCarrinho() != null) order.getCarrinho().setStatus(CarrinhoStatus.PAID);
                }
                if (data.paymentMethodType() != null || data.paymentMethodId() != null) {
                    PagamentoTipo tipo = MercadoPagoStatusMapper.toPagamentoTipo(data.paymentMethodType());
                    pagamento.setPaymentMethodId(PaymentMethodId.findByValue(data.paymentMethodId()));
                    pagamento.setTipo(tipo);
                    if (data.installments() != null) pagamento.setInstallments(data.installments());
                    else if (tipo != null && tipo != PagamentoTipo.CREDIT_CARD) pagamento.setInstallments(1);
                }
                if (stateChanged) domainEvent = new OrderPaidEvent(order);
            }
            case CANCELED -> {
                if (appliesToOrder) markCartCanceled(order);
                if (stateChanged) domainEvent = new CompraCanceladaEvent(order);
            }
            case EXPIRED, FAILED -> {
                if (appliesToOrder) markCartCanceled(order);
                if (stateChanged) domainEvent = new OrderNotCompletedEvent(order, false);
            }
            case REFUNDED -> {
                pagamento.setRefundConfirmedAt(Instant.now());
                if (data.refundId() != null) pagamento.setProviderRefundId(data.refundId());
                if (appliesToOrder) markCartCanceled(order);
                if (stateChanged) domainEvent = new OrderNotCompletedEvent(order, true);
            }
            case ACTION_REQUIRED, CREATED, AT_TERMINAL, PENDING -> { }
        }

        // O detalhe da transação é mais específico (ex.: insufficient_amount) que o detalhe da Order.
        pagamento.setStatusDetail(data.paymentStatusDetail() != null
                ? data.paymentStatusDetail() : data.orderStatusDetail());
        if (appliesToOrder) order.setStatus(remoteStatus);
        if (data.version() != null) pagamento.setProviderEventVersion(data.version());
        pagamento.setProviderEventAt(parseDate(data.eventDate()));

        pagamentoRepository.saveAndFlush(pagamento);
        registrarEvento(pagamento, pagamentoAnterior, data, remoteStatus, providerEventId);
        if (appliesToOrder) {
            orderService.save(order);
            if (order.getCarrinho() != null) carrinhoService.save(order.getCarrinho());
        }
        if (domainEvent != null) eventPublisher.publishEvent(domainEvent);
        if (domainEvent instanceof OrderPaidEvent) {
            log.info("[PAYMENT-CONFIRMADO] orderId={} paymentId={} transition={}->{} valor={} transactionId={}",
                    order.getIdOrder(), pagamento.getIdPagamento(), localStatus, remoteStatus,
                    order.getTotalCobrado(), pagamento.getTransactionId());
        }
        if (stateChanged) {
            eventPublisher.publishEvent(new PaymentEvent(PointPaymentResponse.from(order)));
            log.info("[PAYMENT-RECONCILIATION] orderId={} paymentId={} localStatus={} remoteStatus={} transition={}->{} terminal notified after commit",
                    order.getIdOrder(), pagamento.getIdPagamento(), localStatus, remoteStatus, localStatus, remoteStatus);
        }
        return stateChanged;
    }

    private boolean canApplyAttemptStatus(PagamentoStatus current, PagamentoStatus next) {
        if (current == next) return true;
        if (current == PagamentoStatus.REFUNDED) return false;
        if (current == PagamentoStatus.PROCESSED) return next == PagamentoStatus.REFUNDED;
        if (current == PagamentoStatus.FAILED || current == PagamentoStatus.CANCELED
                || current == PagamentoStatus.EXPIRED) {
            return next == PagamentoStatus.PROCESSED;
        }
        return true;
    }

    private void registrarEvento(PaymentAttempt attempt, PagamentoStatus anterior,
                                  MercadoPagoOrderState data, OrderStatus remoteStatus,
                                  String providerEventId) {
        com.jefiro.app247.domain.model.PaymentEvent event = new com.jefiro.app247.domain.model.PaymentEvent();
        event.setPaymentAttempt(attempt);
        event.setEmpresa(attempt.getEmpresa());
        event.setProvider(attempt.getProvider());
        event.setEventType("ORDER_" + remoteStatus.name());
        event.setStatusAnterior(anterior);
        event.setStatusNovo(attempt.getStatus());
        event.setProviderEventId(providerEventId);
        event.setProviderVersion(data.version());
        Instant occurred = parseDate(data.eventDate());
        event.setOccurredAt(occurred != null ? occurred : Instant.now());
        paymentEventRepository.save(event);
    }

    private String providerEventId(MercadoPagoOrderState data) {
        return data.mercadoPagoOrderId() + ":"
                + (data.version() != null
                ? data.version() : data.status() + ":" + data.eventDate());
    }

    private boolean isStale(PaymentAttempt attempt, Integer receivedVersion) {
        return receivedVersion != null && attempt.getProviderEventVersion() != null
                && receivedVersion <= attempt.getProviderEventVersion();
    }

    private void markCartCanceled(Order order) {
        if (order.getCarrinho() != null) order.getCarrinho().setStatus(CarrinhoStatus.CANCELED);
    }

    private Instant parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (java.time.format.DateTimeParseException e) {
            log.warn("Data de estado Mercado Pago inválida: {}", value);
            return null;
        }
    }
}
