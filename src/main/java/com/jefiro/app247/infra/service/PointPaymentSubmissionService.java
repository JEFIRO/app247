package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.PaymentAttempt;
import com.jefiro.app247.domain.model.dto.OrderResponse;
import com.jefiro.app247.domain.model.dto.PointPaymentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Serializa submissões Point dentro da instância. Em múltiplas instâncias, a
 * chave persistida em PaymentAttempt continua sendo a barreira idempotente.
 */
@Service
public class PointPaymentSubmissionService {
    private static final Logger log = LoggerFactory.getLogger(PointPaymentSubmissionService.class);
    private static final int LOCK_STRIPES = 64;

    private final ReentrantLock[] locks = new ReentrantLock[LOCK_STRIPES];

    @Autowired
    OrderService orderService;
    @Autowired
    MercadoPagoCobrancaService mercadoPagoCobrancaService;
    @Autowired
    PointPaymentPersistenceService persistenceService;
    @Autowired
    PaymentStateTransitionService transitionService;

    public PointPaymentSubmissionService() {
        for (int index = 0; index < locks.length; index++) {
            locks[index] = new ReentrantLock();
        }
    }

    public PointPaymentResponse submitSameAttempt(String orderId, String origin) {
        ReentrantLock lock = locks[Math.floorMod(orderId.hashCode(), locks.length)];
        lock.lock();
        try {
            Order order = orderService.getOrderForReconciliation(orderId);
            PaymentAttempt attempt = order.getPagamento();
            if (attempt == null) {
                throw new IllegalStateException("Order sem tentativa local para submissão Point");
            }
            if (order.getMpOrderId() != null) {
                log.info("[PAYMENT] submissão reutilizada origin={} orderId={} attemptId={} remoteOrderId={} status={}",
                        origin, orderId, attempt.getIdPagamento(), order.getMpOrderId(), order.getStatus());
                return persistenceService.current(orderId);
            }
            if (!persistenceService.markSubmissionStarted(orderId)) {
                return persistenceService.current(orderId);
            }

            log.info("[PAYMENT] submetendo tentativa persistida origin={} orderId={} attemptId={} status={}",
                    origin, orderId, attempt.getIdPagamento(), order.getStatus());
            try {
                OrderResponse remote = mercadoPagoCobrancaService.createRemoteOrder(orderId);
                persistenceService.persistRemoteAcceptance(orderId, remote);
                transitionService.apply(MercadoPagoOrderState.from(remote));
                return persistenceService.current(orderId);
            } catch (RuntimeException error) {
                log.warn("[PAYMENT] resultado remoto desconhecido origin={} orderId={} attemptId={} errorType={}",
                        origin, orderId, attempt.getIdPagamento(), error.getClass().getSimpleName());
                throw error;
            }
        } finally {
            lock.unlock();
        }
    }
}
