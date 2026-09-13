package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.TerminalTelemetryAlert;
import com.jefiro.app247.domain.model.enum_type.TelemetryAlertStatus;
import com.jefiro.app247.domain.model.enum_type.TelemetryAlertType;
import com.jefiro.app247.domain.model.terminal.Terminal;
import com.jefiro.app247.infra.repository.TerminalTelemetryAlertRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelemetryAlertServiceTest {
    @Mock TerminalTelemetryAlertRepository repository;

    @Test
    void abreUmaUnicaOcorrenciaEAtualizaEnquantoProblemaPersistir() {
        var service = new TelemetryAlertService(repository);
        var terminal = terminal();
        when(repository.findByTerminalIdTerminalAndStatusOrderByOpenedAtDesc(
                "terminal-a", TelemetryAlertStatus.ACTIVE)).thenReturn(java.util.List.of());

        service.reconcile(terminal,
                Map.of(TelemetryAlertType.UNDERVOLTAGE, "Subtensão detectada"), Instant.now());

        var captor = ArgumentCaptor.forClass(TerminalTelemetryAlert.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getActiveKey()).isEqualTo("terminal-a:UNDERVOLTAGE");
        assertThat(captor.getValue().getStatus()).isEqualTo(TelemetryAlertStatus.ACTIVE);
    }

    @Test
    void resolveAlertaSemApagarHistorico() {
        var service = new TelemetryAlertService(repository);
        var terminal = terminal();
        var active = new TerminalTelemetryAlert();
        active.setTerminal(terminal);
        active.setType(TelemetryAlertType.UNDERVOLTAGE);
        active.setStatus(TelemetryAlertStatus.ACTIVE);
        active.setActiveKey("terminal-a:UNDERVOLTAGE");
        when(repository.findByTerminalIdTerminalAndStatusOrderByOpenedAtDesc(
                "terminal-a", TelemetryAlertStatus.ACTIVE)).thenReturn(java.util.List.of(active));

        service.reconcile(terminal, Map.of(), Instant.now());

        assertThat(active.getStatus()).isEqualTo(TelemetryAlertStatus.RESOLVED);
        assertThat(active.getResolvedAt()).isNotNull();
        assertThat(active.getActiveKey()).isNull();
        verify(repository).save(active);
        verify(repository, never()).delete(any());
    }

    private Terminal terminal() {
        var terminal = new Terminal();
        terminal.setIdTerminal("terminal-a");
        return terminal;
    }
}
