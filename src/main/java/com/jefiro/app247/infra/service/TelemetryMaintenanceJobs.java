package com.jefiro.app247.infra.service;

import com.jefiro.app247.infra.repository.TerminalRepository;
import com.jefiro.app247.infra.repository.TerminalTelemetryHistoryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

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

    @Scheduled(fixedDelayString = "${terminal.telemetry.offline-check-delay-ms:30000}")
    @Transactional
    public void reconcileOfflineAlerts() {
        Instant now = Instant.now();
        terminalRepository.findAll().stream().filter(t -> Boolean.TRUE.equals(t.getAtivo()))
                .forEach(t -> alertService.reconcileOffline(t, !presenceService.isOnline(t), now));
    }

    @Scheduled(cron = "${terminal.telemetry.retention-cron:0 20 3 * * *}")
    @Transactional
    public void removeExpiredHistory() {
        historyRepository.deleteByReceivedAtBefore(Instant.now().minus(retentionDays, ChronoUnit.DAYS));
    }
}
