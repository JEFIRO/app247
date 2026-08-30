package com.jefiro.app247.infra.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentReconciliationJobs {
    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationJobs.class);

    @Autowired PaymentReconciliationService reconciliationService;

    @EventListener(ApplicationReadyEvent.class)
    public void reconcileOnStartup() {
        try {
            reconciliationService.reconcileRecent("STARTUP");
        } catch (RuntimeException error) {
            log.warn("[PAYMENT-RECONCILIATION] startup falhou sem impedir a aplicação", error);
        }
    }

    @Scheduled(
            fixedDelayString = "${payment.reconciliation.fixed-delay-ms:15000}",
            initialDelayString = "${payment.reconciliation.initial-delay-ms:15000}")
    public void reconcileScheduled() {
        try {
            reconciliationService.reconcileRecent("SCHEDULER");
        } catch (RuntimeException error) {
            log.warn("[PAYMENT-RECONCILIATION] scheduler falhou; próximo ciclo continuará", error);
        }
    }
}
