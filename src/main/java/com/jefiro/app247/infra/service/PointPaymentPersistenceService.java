package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Carrinho;
import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.PaymentAttempt;
import com.jefiro.app247.domain.model.dto.OrderResponse;
import com.jefiro.app247.domain.model.dto.PointPaymentResponse;
import com.jefiro.app247.domain.model.enum_type.order.OrderStatus;
import com.jefiro.app247.domain.model.enum_type.order.StatusDetail;
import com.jefiro.app247.infra.repository.OrderRepository;
import com.jefiro.app247.infra.repository.PagamentoRepository;
import com.jefiro.app247.infra.repository.TerminalRepository;
import com.jefiro.app247.infra.exception.PaymentAlreadyActiveException;
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
        Carrinho carrinho = carrinhoService.getByIdForUpdate(carrinhoId);
        String empresaId = carrinho.getEmpresa().getId();
        empresaService.getEmpresaOperacionalForUpdate(empresaId);
        configurationService.requireConfigured(carrinho.getTerminal(), empresaId);

        String terminalId = carrinho.getIdTerminal();
        terminalRepository.findByIdForPaymentUpdate(terminalId)
                .orElseThrow(() -> new IllegalStateException("Terminal do carrinho não existe"));
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
        boolean paymentCreated = false;

        if (order.getPagamento() == null) {
            PaymentAttempt pagamento = pagamentoRepository.save(new PaymentAttempt(order));
            order.setPagamento(pagamento);
            paymentCreated = true;
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
        pagamento.setUpdatedAt(Instant.now());
        pagamentoRepository.saveAndFlush(pagamento);
        return true;
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
