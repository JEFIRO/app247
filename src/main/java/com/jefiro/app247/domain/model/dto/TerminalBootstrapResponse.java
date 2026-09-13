package com.jefiro.app247.domain.model.dto;

import com.jefiro.app247.domain.model.enum_type.TerminalBootstrapState;

import java.time.Instant;

public record TerminalBootstrapResponse(
        String terminalId,
        TerminalBootstrapState state,
        boolean paymentConfigured,
        String reason,
        Instant resetRequestedAt
) {
}
