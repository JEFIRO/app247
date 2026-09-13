package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.TerminalTelemetryAlert;
import com.jefiro.app247.domain.model.enum_type.TelemetryAlertStatus;
import com.jefiro.app247.domain.model.enum_type.TelemetryAlertType;
import com.jefiro.app247.domain.model.terminal.Terminal;
import com.jefiro.app247.infra.repository.TerminalTelemetryAlertRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TelemetryAlertService {
    private final TerminalTelemetryAlertRepository repository;

    public TelemetryAlertService(TerminalTelemetryAlertRepository repository) { this.repository = repository; }

    public void reconcile(Terminal terminal, Map<TelemetryAlertType, String> observed, Instant now) {
        var activeByType = repository.findByTerminalIdTerminalAndStatusOrderByOpenedAtDesc(
                        terminal.getIdTerminal(), TelemetryAlertStatus.ACTIVE).stream()
                .collect(Collectors.toMap(TerminalTelemetryAlert::getType, Function.identity()));
        for (TelemetryAlertType type : TelemetryAlertType.values()) {
            if (type == TelemetryAlertType.TERMINAL_OFFLINE) continue;
            reconcileOne(terminal, type, observed.get(type), now, activeByType.get(type));
        }
    }

    public void reconcileOffline(Terminal terminal, boolean offline, Instant now) {
        var active = repository.findByTerminalIdTerminalAndTypeAndStatus(
                        terminal.getIdTerminal(), TelemetryAlertType.TERMINAL_OFFLINE,
                        TelemetryAlertStatus.ACTIVE).orElse(null);
        reconcileOne(terminal, TelemetryAlertType.TERMINAL_OFFLINE,
                offline ? "Terminal sem heartbeat dentro do limite configurado" : null, now, active);
    }

    private void reconcileOne(Terminal terminal, TelemetryAlertType type, String message,
                              Instant now, TerminalTelemetryAlert current) {
        if (message != null) {
            TerminalTelemetryAlert alert = current;
            if (alert == null) {
                var created = new TerminalTelemetryAlert();
                created.setTerminal(terminal);
                created.setType(type);
                created.setStatus(TelemetryAlertStatus.ACTIVE);
                created.setOpenedAt(now);
                created.setActiveKey(terminal.getIdTerminal() + ":" + type.name());
                alert = created;
            }
            alert.setMessage(message);
            alert.setLastObservedAt(now);
            repository.save(alert);
        } else if (current != null) {
            current.setStatus(TelemetryAlertStatus.RESOLVED);
            current.setResolvedAt(now);
            current.setLastObservedAt(now);
            current.setActiveKey(null);
            repository.save(current);
        }
    }
}
