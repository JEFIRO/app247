package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.terminal.Terminal;
import com.jefiro.app247.infra.repository.TerminalRepository;
import com.jefiro.app247.infra.repository.TerminalTelemetryHistoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
public class TelemetryMaintenanceJobs {
    private final TerminalRepository terminalRepository;
    private final TerminalPresenceService presenceService;
    private final TelemetryAlertService alertService;
    private final TerminalTelemetryHistoryRepository historyRepository;
    private final int retentionDays;

    public TelemetryMaintenanceJobs(TerminalRepository terminalRepository,
                                    TerminalPresenceService presenceService,
                                    TelemetryAlertService alertService,
                                    TerminalTelemetryHistoryRepository historyRepository,
                                    @Value("${terminal.telemetry.retention-days:30}") int retentionDays) {
        this.terminalRepository = terminalRepository;
        this.presenceService = presenceService;
        this.alertService = alertService;
        this.historyRepository = historyRepository;
        this.retentionDays = retentionDays;
    }

    /**
     * Sem transação aqui: cada alerta confirma sozinho. Inserir alerta toma lock compartilhado na
     * linha do Terminal (FK); numa transação única, isso travaria as cobranças até o fim da varredura.
     */
    @Scheduled(fixedDelayString = "${terminal.telemetry.offline-check-delay-ms:30000}")
    public void reconcileOfflineAlerts() {
        Instant now = Instant.now();
        terminalRepository.findAll().stream().filter(t -> Boolean.TRUE.equals(t.getAtivo()))
                .forEach(t -> reconcileOffline(t, now));
    }

    private void reconcileOffline(Terminal terminal, Instant now) {
        try {
            alertService.reconcileOffline(terminal, !presenceService.isOnline(terminal), now);
        } catch (RuntimeException error) {
            log.warn("[TERMINAL-TELEMETRY] falha ao reconciliar alerta offline terminal={}",
                    terminal.getIdTerminal(), error);
        }
    }

    @Scheduled(cron = "${terminal.telemetry.retention-cron:0 20 3 * * *}")
    @Transactional
    public void removeExpiredHistory() {
        historyRepository.deleteByReceivedAtBefore(Instant.now().minus(retentionDays, ChronoUnit.DAYS));
    }
}
