package com.jefiro.app247.infra.event;

import java.time.Instant;

public record TerminalFactoryResetRequiredEvent(
        String type,
        String terminalId,
        String state,
        String reason,
        Instant requestedAt
) {
    public TerminalFactoryResetRequiredEvent(String terminalId, String reason, Instant requestedAt) {
        this("TERMINAL_FACTORY_RESET_REQUIRED", terminalId, "RESET_REQUIRED", reason, requestedAt);
    }
}
