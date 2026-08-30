package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.Pagamento;
import com.jefiro.app247.domain.model.dto.PointPaymentResponse;
import com.jefiro.app247.domain.model.enum_type.CarrinhoStatus;
import com.jefiro.app247.domain.model.enum_type.PagamentoTipo;
import com.jefiro.app247.domain.model.enum_type.PaymentMethodId;
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

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.NoSuchElementException;

@Service
public class PaymentStateTransitionService {
    private static final Logger log = LoggerFactory.getLogger(PaymentStateTransitionService.class);

    @Autowired OrderService orderService;
    @Autowired CarrinhoService carrinhoService;
    @Autowired PagamentoRepository pagamentoRepository;
    @Autowired ApplicationEventPublisher eventPublisher;

    @Transactional
    public boolean apply(MercadoPagoOrderState data) {
        OrderStatus remoteStatus = OrderStatus.findByValue(data.status());
        if (remoteStatus == null) {
            throw new UnknownExternalStatusException("Status Mercado Pago desconhecido: " + data.status());
        }

        Order order;
        try {
            order = orderService.getOrderForUpdate(data.externalReference());
        } catch (NoSuchElementException e) {
            log.warn("Estado Mercado Pago ignorado: Order local não encontrada; externalReference={}, mpOrderId={}",
                    data.externalReference(), data.mercadoPagoOrderId());
            return false;
        }
        if (order.getMpOrderId() != null && data.mercadoPagoOrderId() != null
                && !order.getMpOrderId().equals(data.mercadoPagoOrderId())) {
            throw new IllegalStateException("Order Mercado Pago divergente da cobrança persistida");
        }
        if (isStale(order, data.version())) {
            log.info("Estado Mercado Pago obsoleto ignorado: orderId={}, version={}, currentVersion={}",
                    order.getIdOrder(), data.version(), order.getMpEventVersion());
            return false;
        }

        OrderStatus localStatus = order.getStatus();
        boolean stateChanged = localStatus != remoteStatus;
        if (stateChanged && localStatus != null && !localStatus.canTransitionTo(remoteStatus)) {
            log.warn("Transição Mercado Pago inválida ignorada: orderId={}, current={}, received={}, version={}",
                    order.getIdOrder(), localStatus, remoteStatus, data.version());
            return false;
        }
        Pagamento pagamento = order.getPagamento();
        if (pagamento == null) {
            throw new IllegalStateException("Order " + order.getIdOrder() + " não possui Pagamento vinculado");
        }

        pagamento.setStatusDetail(data.paymentStatusDetail());
        pagamento.setUpdatedAt(LocalDateTime.now());
        if (data.paymentId() != null) pagamento.setTransactionId(data.paymentId());
        pagamento.setStatus(MercadoPagoStatusMapper.toPagamentoStatus(remoteStatus));

        Object domainEvent = null;
        switch (remoteStatus) {
            case PROCESSED -> {
                LocalDateTime paidAt = pagamento.getPaidAt() != null ? pagamento.getPaidAt() : LocalDateTime.now();
                pagamento.setPaidAt(paidAt);
                order.setPaidAt(paidAt);
                if (order.getCarrinho() != null) order.getCarrinho().setStatus(CarrinhoStatus.PAID);
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
                markCartCanceled(order);
                if (stateChanged) domainEvent = new CompraCanceladaEvent(order);
            }
            case EXPIRED, FAILED -> {
                markCartCanceled(order);
                if (stateChanged) domainEvent = new OrderNotCompletedEvent(order, false);
            }
            case REFUNDED -> {
                markCartCanceled(order);
                if (stateChanged) domainEvent = new OrderNotCompletedEvent(order, true);
            }
            case ACTION_REQUIRED, CREATED, AT_TERMINAL, PENDING -> { }
        }

        order.setMpStatus(remoteStatus);
        order.setMpStatusDetail(StatusDetail.findByValue(data.orderStatusDetail()));
        order.setStatus(remoteStatus);
        if (data.version() != null) order.setMpEventVersion(data.version());
        order.setMpEventDate(parseDate(data.eventDate()));

        pagamentoRepository.saveAndFlush(pagamento);
        orderService.save(order);
        if (order.getCarrinho() != null) carrinhoService.save(order.getCarrinho());
        if (domainEvent != null) eventPublisher.publishEvent(domainEvent);
        if (stateChanged) {
            eventPublisher.publishEvent(new PaymentEvent(PointPaymentResponse.from(order)));
            log.info("[PAYMENT-RECONCILIATION] orderId={} paymentId={} localStatus={} remoteStatus={} transition={}->{} terminal notified after commit",
                    order.getIdOrder(), pagamento.getIdPagamento(), localStatus, remoteStatus, localStatus, remoteStatus);
        }
        return stateChanged;
    }

    private boolean isStale(Order order, Integer receivedVersion) {
        return receivedVersion != null && order.getMpEventVersion() != null
                && receivedVersion <= order.getMpEventVersion();
    }

    private void markCartCanceled(Order order) {
        if (order.getCarrinho() != null) order.getCarrinho().setStatus(CarrinhoStatus.CANCELED);
    }

    private LocalDateTime parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (java.time.format.DateTimeParseException e) {
            log.warn("Data de estado Mercado Pago inválida: {}", value);
            return null;
        }
    }
}
