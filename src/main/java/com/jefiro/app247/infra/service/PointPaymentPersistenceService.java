package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Carrinho;
import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.PaymentAttempt;
import com.jefiro.app247.domain.model.dto.OrderResponse;
import com.jefiro.app247.domain.model.dto.PointPaymentResponse;
import com.jefiro.app247.domain.model.appclient.CustomerCoupon;
import com.jefiro.app247.domain.model.enum_type.CarrinhoStatus;
import com.jefiro.app247.domain.model.enum_type.PagamentoStatus;
import com.jefiro.app247.domain.model.enum_type.order.OrderStatus;
import com.jefiro.app247.domain.model.enum_type.order.StatusDetail;
import com.jefiro.app247.infra.repository.OrderRepository;
import com.jefiro.app247.infra.repository.PagamentoRepository;
import com.jefiro.app247.infra.repository.TerminalRepository;
import com.jefiro.app247.infra.exception.PaymentAlreadyActiveException;
import com.jefiro.app247.infra.exception.ApiBusinessException;
import com.jefiro.app247.infra.event.OrderNotCompletedEvent;
import com.jefiro.app247.infra.event.OrderPaymentRetryEvent;
import com.jefiro.app247.infra.repository.CustomerCouponRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.time.Instant;

/**
 * Fronteiras locais do início Point. A chamada HTTP externa nunca ocorre
 * dentro destas transações.
 */
@Service
public class PointPaymentPersistenceService {
    private static final Logger log = LoggerFactory.getLogger(PointPaymentPersistenceService.class);
    private static final List<OrderStatus> UNRESOLVED = List.of(
            OrderStatus.PENDING,
            OrderStatus.CREATED,
            OrderStatus.AT_TERMINAL,
            OrderStatus.ACTION_REQUIRED
    );

    private final CarrinhoService carrinhoService;
    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final PagamentoRepository pagamentoRepository;
    @Autowired
    MercadoPagoOperationalConfigurationService configurationService;
    @Autowired
    EmpresaService empresaService;
    @Autowired
    TerminalRepository terminalRepository;
    @Autowired
    CustomerCouponRepository couponRepository;
    @Autowired
    OrderPriceFinalizationService priceFinalizationService;
    @Autowired
    TerminalRequestAuthorizationService terminalAuthorization;
    @Autowired
    ApplicationEventPublisher eventPublisher;

    public PointPaymentPersistenceService(
            CarrinhoService carrinhoService,
            OrderService orderService,
            OrderRepository orderRepository,
            PagamentoRepository pagamentoRepository
    ) {
        this.carrinhoService = carrinhoService;
        this.orderService = orderService;
        this.orderRepository = orderRepository;
        this.pagamentoRepository = pagamentoRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PreparedAttempt prepare(String carrinhoId) {
        return prepare(carrinhoId, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PreparedAttempt prepare(String carrinhoId, String authenticatedTerminalId) {
        Carrinho carrinho = carrinhoService.getByIdForUpdate(carrinhoId);
        if (authenticatedTerminalId != null
                && !authenticatedTerminalId.equals(carrinho.getIdTerminal())) {
            throw new ApiBusinessException(HttpStatus.NOT_FOUND, "CART_NOT_OWNED",
                    "Carrinho não pertence ao Terminal autenticado");
        }
        String empresaId = carrinho.getEmpresa().getId();
        empresaService.getEmpresaOperacionalForShare(empresaId);
        configurationService.requireConfigured(carrinho.getTerminal(), empresaId);

        String terminalId = carrinho.getIdTerminal();
        var lockedTerminal = terminalRepository.findByIdForPaymentUpdate(terminalId)
                .orElseThrow(() -> new IllegalStateException("Terminal do carrinho não existe"));
        terminalAuthorization.requireCartOwnership(lockedTerminal, carrinho);
        String currentOrderId = orderRepository.findByCarrinhoIdCarrinho(carrinhoId)
                .map(Order::getIdOrder)
                .orElse(null);
        for (String activeOrderId : orderRepository.findUnresolvedPaymentOrderIdsForTerminal(
                terminalId, UNRESOLVED, PageRequest.of(0, 2))) {
            if (!activeOrderId.equals(currentOrderId)) {
                Order activeOrder = orderService.getOrderForReconciliation(activeOrderId);
                log.warn("[PAYMENT] nova tentativa bloqueada terminalId={} cartId={} activeOrderId={} activeAttemptId={} status={}",
                        terminalId, carrinhoId, activeOrder.getIdOrder(),
                        activeOrder.getPagamento() != null
                                ? activeOrder.getPagamento().getIdPagamento() : null,
                        activeOrder.getStatus());
                throw new PaymentAlreadyActiveException(activeOrder);
            }
        }

        Order order = orderService.criarCobranca(carrinho);
        terminalAuthorization.requireOrderOwnership(lockedTerminal, order);
        boolean paymentCreated;
        PaymentAttempt current = order.getPagamento();

        if (current == null) {
            priceFinalizationService.finalizeForPayment(order, Instant.now());
            PaymentAttempt pagamento = createAttempt(order);
            paymentCreated = true;
        } else if (current.getStatus().isActive()) {
            priceFinalizationService.validateFrozen(order, Instant.now());
            paymentCreated = false;
        } else if (current.getStatus().allowsFinancialRetry()) {
            CustomerCoupon coupon = order.getCoupon() == null ? null
                    : couponRepository.findForUpdate(order.getCoupon().getId(), empresaId)
                    .orElseThrow(() -> new ApiBusinessException(HttpStatus.CONFLICT,
                            "COUPON_RESERVATION_MISMATCH", "Cupom da Order não foi encontrado"));
            priceFinalizationService.prepareFinancialRetry(order, coupon, Instant.now());
            PaymentAttempt pagamento = createAttempt(order);
            eventPublisher.publishEvent(new OrderPaymentRetryEvent(order, pagamento.getAttemptNumber()));
            paymentCreated = true;
        } else {
            // Aprovada ou estornada: Terminal que perdeu a resposta inicial descobre o desfecho por aqui.
            paymentCreated = false;
        }

        orderRepository.saveAndFlush(order);
        pagamentoRepository.flush();
        log.info("[PAYMENT-BACKEND] tentativa local confirmada orderId={} paymentId={} status={}",
                order.getIdOrder(), order.getPagamento().getIdPagamento(), order.getStatus());

        // Somente quem criou a tentativa local pode executar o POST remoto.
        // Chamadas concorrentes encontram o Pagamento existente e o reutilizam.
        return new PreparedAttempt(
                order.getIdOrder(), order.getPagamento().getIdPagamento(),
                paymentCreated && order.getMpOrderId() == null,
                order.getMpOrderId()
        );
    }

    private PaymentAttempt createAttempt(Order order) {
        if (order.getTotalCobrado() == null || order.getTotalCobrado().signum() <= 0) {
            throw new ApiBusinessException(HttpStatus.CONFLICT, "ORDER_PRICE_NOT_FINALIZED",
                    "Total final da Order deve ser positivo");
        }
        PaymentAttempt pagamento = pagamentoRepository.save(new PaymentAttempt(order));
        if (pagamento.getValor().compareTo(order.getTotalCobrado()) != 0) {
            throw new ApiBusinessException(HttpStatus.CONFLICT, "ORDER_PRICE_NOT_FINALIZED",
                    "Valor da tentativa diverge do total congelado da Order");
        }
        order.setPagamento(pagamento);
        return pagamento;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PointPaymentResponse persistRemoteAcceptance(String orderId, OrderResponse response) {
        Order order = orderService.getOrderForUpdate(orderId);
        PaymentAttempt pagamento = requirePayment(order);

        if (response.id() == null || response.id().isBlank()) {
            throw new IllegalArgumentException("Resposta Mercado Pago sem ID de Order");
        }
        if (order.getMpOrderId() != null && !order.getMpOrderId().equals(response.id())) {
            throw new IllegalStateException("Order Mercado Pago divergente da tentativa local");
        }

        OrderStatus remoteStatus = OrderStatus.findByValue(response.status());

        order.setMpOrderId(response.id());
        order.setMpType(response.type());
        order.setMpUserId(response.userId());
        if (remoteStatus != null
                && (order.getStatus() == null || order.getStatus() == OrderStatus.PENDING)
                && (remoteStatus == OrderStatus.CREATED || remoteStatus == OrderStatus.AT_TERMINAL)) {
            order.setStatus(remoteStatus);
            order.setMpStatus(remoteStatus);
            order.setMpStatusDetail(StatusDetail.findByValue(response.statusDetail()));
        }
        if (response.config() != null && response.config().point() != null) {
            order.setMpTerminalId(response.config().point().terminalId());
        }
        if (response.transactions() != null && response.transactions().payments() != null
                && !response.transactions().payments().isEmpty()
                && response.transactions().payments().get(0).id() != null) {
            pagamento.setTransactionId(response.transactions().payments().get(0).id());
        }

        pagamentoRepository.save(pagamento);
        orderRepository.saveAndFlush(order);
        log.info("[PAYMENT-BACKEND] aceite remoto persistido orderId={} paymentId={} mpOrderId={} status={}",
                order.getIdOrder(), pagamento.getIdPagamento(), order.getMpOrderId(), order.getStatus());
        return PointPaymentResponse.from(order);
    }

    @Transactional(readOnly = true)
    public PointPaymentResponse current(String orderId) {
        return PointPaymentResponse.from(orderService.getOrderForReconciliation(orderId));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markSubmissionStarted(String orderId) {
        Order order = orderService.getOrderForUpdate(orderId);
        PaymentAttempt pagamento = requirePayment(order);
        if (order.getMpOrderId() != null) {
            return false;
        }
        if (pagamento.getStatus() != PagamentoStatus.PENDING
                && pagamento.getStatus() != PagamentoStatus.UNKNOWN) {
            return false;
        }
        pagamento.setUpdatedAt(Instant.now());
        pagamentoRepository.saveAndFlush(pagamento);
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PointPaymentResponse markUnknown(String orderId, String attemptId, String detail) {
        PaymentAttempt attempt = pagamentoRepository.findByIdForUpdate(attemptId)
                .orElseThrow(() -> new IllegalStateException("Tentativa de pagamento não existe"));
        if (!orderId.equals(attempt.getOrder().getIdOrder())) {
            throw new IllegalStateException("Tentativa pertence a outra Order");
        }
        if (attempt.getStatus().isActive()) {
            attempt.setStatus(PagamentoStatus.UNKNOWN);
            attempt.setStatusDetail(detail);
            pagamentoRepository.saveAndFlush(attempt);
        }
        return PointPaymentResponse.from(attempt.getOrder());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PointPaymentResponse markDefinitiveSubmissionFailure(
            String orderId, String attemptId, String detail) {
        PaymentAttempt attempt = pagamentoRepository.findByIdForUpdate(attemptId)
                .orElseThrow(() -> new IllegalStateException("Tentativa de pagamento não existe"));
        Order order = attempt.getOrder();
        if (!orderId.equals(order.getIdOrder())) {
            throw new IllegalStateException("Tentativa pertence a outra Order");
        }
        if (attempt.getStatus().isActive()) {
            attempt.setStatus(PagamentoStatus.FAILED);
            attempt.setStatusDetail(detail);
            attempt.setFinishedAt(Instant.now());
            order.setStatus(OrderStatus.FAILED);
            if (order.getCarrinho() != null) order.getCarrinho().setStatus(CarrinhoStatus.CANCELED);
            pagamentoRepository.saveAndFlush(attempt);
            orderRepository.saveAndFlush(order);
            eventPublisher.publishEvent(new OrderNotCompletedEvent(order, false));
        }
        return PointPaymentResponse.from(order);
    }

    private PaymentAttempt requirePayment(Order order) {
        if (order.getPagamento() == null) {
            log.error("[PAYMENT-INTEGRITY] ORDER_WITHOUT_PAYMENT orderId={} mpOrderId={} status={}",
                    order.getIdOrder(), order.getMpOrderId(), order.getStatus());
            throw new IllegalStateException("Order local sem tentativa de Pagamento");
        }
        return order.getPagamento();
    }

    public record PreparedAttempt(String orderId, String paymentAttemptId,
                                  boolean shouldSubmit, String mercadoPagoOrderId) {
        /** Compatibilidade transitória para consumidores internos anteriores à separação Order/Attempt. */
        public PreparedAttempt(String orderId, boolean shouldSubmit, String mercadoPagoOrderId) {
            this(orderId, null, shouldSubmit, mercadoPagoOrderId);
        }
    }
}
