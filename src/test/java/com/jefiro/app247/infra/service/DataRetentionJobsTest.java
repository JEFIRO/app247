package com.jefiro.app247.infra.service;

import com.jefiro.app247.infra.repository.TerminalTelemetryAlertRepository;
import com.jefiro.app247.infra.repository.WebhookEventRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DataRetentionJobsTest {
    @Test
    void limpezaAtingeSomenteDadosOperacionaisConfigurados() {
        TerminalTelemetryAlertRepository alerts = mock(TerminalTelemetryAlertRepository.class);
        WebhookEventRepository webhooks = mock(WebhookEventRepository.class);
        DataRetentionJobs jobs = new DataRetentionJobs(alerts, webhooks, 365, 730);

        jobs.cleanupOperationalData();

        verify(alerts).deleteResolvedBefore(any(Instant.class));
        verify(webhooks).deleteProcessedBefore(any(Instant.class));
        assertThat(Arrays.stream(DataRetentionJobs.class.getDeclaredConstructors())
                .flatMap(c -> Arrays.stream(c.getParameterTypes()))
                .map(Class::getSimpleName))
                .noneMatch(name -> name.contains("Order") || name.contains("Payment")
                        || name.contains("Pagamento") || name.contains("MovimentacaoEstoque"));
    }
}
