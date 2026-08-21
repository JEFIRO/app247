package com.jefiro.app247.infra.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jefiro.app247.domain.model.Carrinho;
import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.Pagamento;
import com.jefiro.app247.domain.model.dto.OrderResponse;
import com.jefiro.app247.domain.model.dto.PointPaymentResponse;
import com.jefiro.app247.domain.model.mapper.MercadoPagoStatusMapper;
import com.jefiro.app247.domain.model.dto.mercadopago.OrderWebhookNotification;
import com.jefiro.app247.domain.model.enum_type.PagamentoStatus;
import com.jefiro.app247.domain.model.enum_type.PagamentoTipo;
import com.jefiro.app247.domain.model.enum_type.PaymentMethodId;
import com.jefiro.app247.domain.model.enum_type.order.OrderStatus;
import com.jefiro.app247.domain.model.enum_type.order.StatusDetail;
import com.jefiro.app247.infra.event.CompraCanceladaEvent;
import com.jefiro.app247.infra.event.OrderNotCompletedEvent;
import com.jefiro.app247.infra.event.OrderPaidEvent;
import com.jefiro.app247.infra.event.PaymentEvent;
import com.jefiro.app247.infra.exception.UnknownExternalStatusException;
import com.jefiro.app247.infra.repository.PagamentoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class PagamentoService {
    private static final Logger log = LoggerFactory.getLogger(PagamentoService.class);
    @Autowired
    OrderService orderService;
    @Autowired
    CarrinhoService carrinhoService;
    @Autowired
    PagamentoRepository pagamentoRepository;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    ApplicationEventPublisher eventPublisher;
    @Autowired
    MercadoPagoOrderQueryService mercadoPagoOrderQueryService;

    @Transactional
    public PointPaymentResponse gerarCobranca(String carrinho_id) {
        Carrinho carrinho = carrinhoService.getByIdForUpdate(carrinho_id);
        Order order = orderService.criarCobranca(carrinho);
        return PointPaymentResponse.from(order);
    }

    public Pagamento save(Pagamento pagamento) {
        return pagamentoRepository.saveAndFlush(pagamento);

    }


    @Transactional
    public void atualizarPagamento(String json) throws JsonProcessingException {
        OrderWebhookNotification notification = mapper.readValue(json, OrderWebhookNotification.class);
        WebhookOrderData data = normalize(notification);

        OrderStatus orderStatus = OrderStatus.findByValue(data.status());
        if (orderStatus == null) {
            log.error("Webhook Mercado Pago ignorado por status desconhecido: status={}, mpOrderId={}",
                    data.status(), data.mercadoPagoOrderId());
            throw new UnknownExternalStatusException(
                    "Status Mercado Pago desconhecido: " + data.status());
        }

        Order order;
        try {
            order = orderService.getOrderForUpdate(data.externalReference());
        } catch (NoSuchElementException e) {
            log.warn("Webhook ignorado: Order local não encontrada; externalReference={}, mpOrderId={}",
                    data.externalReference(), data.mercadoPagoOrderId());
            return;
        }

        if (isStale(order, data.version())) {
            log.info("Webhook Mercado Pago obsoleto ignorado: orderId={}, version={}, currentVersion={}",
                    order.getIdOrder(), data.version(), order.getMpEventVersion());
            return;
        }
        OrderStatus currentStatus = order.getStatus();
        boolean stateChanged = currentStatus != orderStatus;
        if (stateChanged && currentStatus != null && !currentStatus.canTransitionTo(orderStatus)) {
            log.warn("Transição Mercado Pago inválida ignorada: orderId={}, current={}, received={}, version={}",
                    order.getIdOrder(), currentStatus, orderStatus, data.version());
            return;
        }
        Pagamento pagamento = order.getPagamento();

        if (pagamento == null) {
            throw new IllegalStateException(
                    "Order " + order.getIdOrder() + " não possui Pagamento vinculado");
        }

        pagamento.setStatusDetail(data.paymentStatusDetail());
        pagamento.setUpdatedAt(LocalDateTime.now());

        if (data.paymentId() != null) {
            pagamento.setTransactionId(data.paymentId());
        }

        Object domainEvent = null;
        pagamento.setStatus(MercadoPagoStatusMapper.toPagamentoStatus(orderStatus));
        switch (orderStatus) {
            case PROCESSED -> {
                LocalDateTime paidAt = pagamento.getPaidAt() != null
                        ? pagamento.getPaidAt() : LocalDateTime.now();
                pagamento.setPaidAt(paidAt);
                order.setPaidAt(paidAt);
                if (order.getCarrinho() != null) {
                    order.getCarrinho().setStatus(com.jefiro.app247.domain.model.enum_type.CarrinhoStatus.PAID);
                }

                if (data.paymentMethodType() != null || data.paymentMethodId() != null) {
                    PagamentoTipo tipo = MercadoPagoStatusMapper.toPagamentoTipo(data.paymentMethodType());

                    pagamento.setPaymentMethodId(PaymentMethodId.findByValue(data.paymentMethodId()));
                    pagamento.setTipo(tipo);

                    if (data.installments() != null) {
                        pagamento.setInstallments(data.installments());
                    } else if (tipo != null && tipo != PagamentoTipo.CREDIT_CARD) {
                        pagamento.setInstallments(1);
                    }
                }
                if (stateChanged) domainEvent = new OrderPaidEvent(order);
            }
            case CANCELED -> {
                markCartCanceled(order);
                if (stateChanged) domainEvent = new CompraCanceladaEvent(order);
            }
            case EXPIRED -> {
                markCartCanceled(order);
                if (stateChanged) domainEvent = new OrderNotCompletedEvent(order, false);
            }
            case FAILED -> {
                markCartCanceled(order);
                if (stateChanged) domainEvent = new OrderNotCompletedEvent(order, false);
            }
            case REFUNDED -> {
                markCartCanceled(order);
                if (stateChanged) domainEvent = new OrderNotCompletedEvent(order, true);
            }
            case ACTION_REQUIRED, CREATED, AT_TERMINAL, PENDING -> { }
        }

        order.setMpStatus(orderStatus);
        order.setMpStatusDetail(StatusDetail.findByValue(data.orderStatusDetail()));
        order.setStatus(orderStatus);
        if (data.version() != null) {
            order.setMpEventVersion(data.version());
        }
        order.setMpEventDate(parseDate(data.eventDate()));

        save(pagamento);
        orderService.save(order);
        if (order.getCarrinho() != null) {
            carrinhoService.save(order.getCarrinho());
        }
        if (domainEvent != null) {
            eventPublisher.publishEvent(domainEvent);
        }
        if (stateChanged) {
            eventPublisher.publishEvent(new PaymentEvent(PointPaymentResponse.from(order)));
            log.info("Status Point atualizado e notificação preparada: orderId={}, terminalId={}, status={}",
                    order.getIdOrder(), order.getCarrinho().getIdTerminal(), orderStatus);
        }

    }

    private WebhookOrderData normalize(OrderWebhookNotification notification) {
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
            return new WebhookOrderData(
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
        return new WebhookOrderData(
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

    private record WebhookOrderData(
            String externalReference,
            String status,
            String orderStatusDetail,
            String paymentStatusDetail,
            String paymentId,
            String paymentMethodId,
            String paymentMethodType,
            Integer installments,
            String mercadoPagoOrderId,
            Integer version,
            String eventDate
    ) {
    }

    private boolean isStale(Order order, Integer receivedVersion) {
        return receivedVersion != null && order.getMpEventVersion() != null
                && receivedVersion <= order.getMpEventVersion();
    }

    private void markCartCanceled(Order order) {
        if (order.getCarrinho() != null) {
            order.getCarrinho().setStatus(
                    com.jefiro.app247.domain.model.enum_type.CarrinhoStatus.CANCELED);
        }
    }

    private LocalDateTime parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (java.time.format.DateTimeParseException e) {
            log.warn("Data de webhook Mercado Pago inválida: {}", value);
            return null;
        }
    }

}
