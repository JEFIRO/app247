package com.jefiro.app247.infra.service;

import com.jefiro.app247.infra.repository.TerminalTelemetryAlertRepository;
import com.jefiro.app247.infra.repository.WebhookEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class DataRetentionJobs {
    private final TerminalTelemetryAlertRepository alertRepository;
    private final WebhookEventRepository webhookRepository;
    private final int alertDays;
    private final int webhookDays;

    public DataRetentionJobs(TerminalTelemetryAlertRepository alertRepository,
                             WebhookEventRepository webhookRepository,
                             @Value("${retention.telemetry-alert-days:365}") int alertDays,
                             @Value("${retention.webhook-days:365}") int webhookDays) {
        this.alertRepository = alertRepository;
        this.webhookRepository = webhookRepository;
        this.alertDays = alertDays;
        this.webhookDays = webhookDays;
    }

    @Scheduled(cron = "${retention.cleanup-cron:0 40 3 * * *}")
    @Transactional
    public void cleanupOperationalData() {
        alertRepository.deleteResolvedBefore(Instant.now().minus(alertDays, ChronoUnit.DAYS));
        webhookRepository.deleteProcessedBefore(Instant.now().minus(webhookDays, ChronoUnit.DAYS));
    }
}
