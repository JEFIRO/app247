package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.terminal.Terminal;
import com.jefiro.app247.infra.repository.TerminalRepository;
import com.jefiro.app247.infra.repository.TerminalTelemetryHistoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelemetryMaintenanceJobsTest {
    private final TerminalRepository terminals = mock(TerminalRepository.class);
    private final TerminalPresenceService presence = mock(TerminalPresenceService.class);
    private final TelemetryAlertService alerts = mock(TelemetryAlertService.class);
    private final TelemetryMaintenanceJobs jobs = new TelemetryMaintenanceJobs(
            terminals, presence, alerts, mock(TerminalTelemetryHistoryRepository.class), 30);

    @Test
    void falhaEmUmTerminalNaoInterrompeOsDemais() {
        Terminal comFalha = terminal(true);
        Terminal seguinte = terminal(true);
        when(terminals.findAll()).thenReturn(List.of(comFalha, seguinte));
        doThrow(new IllegalStateException("falha")).when(alerts)
                .reconcileOffline(eq(comFalha), eq(true), any(Instant.class));

        jobs.reconcileOfflineAlerts();

        verify(alerts).reconcileOffline(eq(seguinte), eq(true), any(Instant.class));
    }

    @Test
    void terminalInativoNaoGeraAlerta() {
        Terminal inativo = terminal(false);
        when(terminals.findAll()).thenReturn(List.of(inativo));

        jobs.reconcileOfflineAlerts();

        verify(alerts, never()).reconcileOffline(any(), any(Boolean.class), any());
    }

    @Test
    void cadaAlertaConfirmaNaPropriaTransacao() throws Exception {
        assertThat(TelemetryMaintenanceJobs.class.getMethod("reconcileOfflineAlerts")
                .isAnnotationPresent(Transactional.class)).isFalse();
        assertThat(TelemetryAlertService.class
                .getMethod("reconcileOffline", Terminal.class, boolean.class, Instant.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
    }

    private Terminal terminal(boolean ativo) {
        Terminal terminal = mock(Terminal.class);
        when(terminal.getAtivo()).thenReturn(ativo);
        return terminal;
    }
}
