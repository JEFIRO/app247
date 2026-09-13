package com.jefiro.app247.domain.model.dto.admin;

import com.jefiro.app247.domain.model.enum_type.TelemetryAlertStatus;
import com.jefiro.app247.domain.model.enum_type.TelemetryAlertType;
import com.jefiro.app247.domain.model.enum_type.TerminalOperationalStatus;

import java.time.Instant;

public record AdminAlertResponse(
        String id,
        String terminalId,
        String terminalNome,
        String condominioId,
        String condominioNome,
        TelemetryAlertType tipo,
        TelemetryAlertStatus status,
        TerminalOperationalStatus statusOperacional,
        String mensagem,
        Instant abertoEm,
        Instant ultimaOcorrenciaEm,
        Instant resolvidoEm
) {
    @com.fasterxml.jackson.annotation.JsonProperty("severidade")
    public String severidade() {
        if (tipo == TelemetryAlertType.TERMINAL_OFFLINE
                || statusOperacional == TerminalOperationalStatus.CRITICO
                || statusOperacional == TerminalOperationalStatus.OFFLINE) {
            return "CRITICAL";
        }
        return "WARNING";
    }
}
