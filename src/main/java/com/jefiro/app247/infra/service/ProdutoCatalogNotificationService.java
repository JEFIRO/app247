package com.jefiro.app247.infra.service;

import com.jefiro.app247.infra.event.ProdutoCatalogChangedEvent;
import com.jefiro.app247.infra.event.ProdutoSyncRequiredMessage;
import com.jefiro.app247.infra.repository.TerminalRepository;
import com.jefiro.app247.infra.websocket.PaymentWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashSet;

@Service
public class ProdutoCatalogNotificationService {
    private static final Logger log = LoggerFactory.getLogger(ProdutoCatalogNotificationService.class);

    @Autowired private TerminalRepository terminalRepository;
    @Autowired private PaymentWebSocketHandler webSocketHandler;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void notificarDepoisDoCommit(ProdutoCatalogChangedEvent event) {
        try {
            notificar(event);
        } catch (RuntimeException e) {
            log.warn("[PRODUCT-CATALOG] falha ao notificar produto={} evento={} condominios={}",
                    event.productId(), event.reason(), event.condominiumIds().size(), e);
        }
    }

    private void notificar(ProdutoCatalogChangedEvent event) {
        if (event.condominiumIds().isEmpty()) {
            log.info("[PRODUCT-CATALOG] produto={} evento={} condominios=0 terminaisNotificados=0",
                    event.productId(), event.reason());
            return;
        }

        var terminalIds = new LinkedHashSet<>(
                terminalRepository.findIdsByCondominiumIds(event.condominiumIds()));
        ProdutoSyncRequiredMessage message = new ProdutoSyncRequiredMessage(event);
        long notificados = terminalIds.stream()
                .filter(terminalId -> webSocketHandler.sendToTerminal(terminalId, message))
                .count();

        log.info("[PRODUCT-CATALOG] produto={} evento={} condominios={} terminaisNotificados={}",
                event.productId(), event.reason(), event.condominiumIds().size(), notificados);
    }
}
