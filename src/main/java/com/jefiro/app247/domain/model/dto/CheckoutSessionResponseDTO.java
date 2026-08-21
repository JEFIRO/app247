package com.jefiro.app247.domain.model.dto;

import com.jefiro.app247.domain.model.enum_type.SessionStatus;

import java.time.Instant;

public record CheckoutSessionResponseDTO(
        String sessionId,
        String carrinhoId,
        String terminalId,
        String qrCodeUrl,
        SessionStatus status,
        Instant expiresAt

) {
    public CheckoutSessionResponseDTO(CheckoutSession cs) {
        this(cs.getSessionId(), cs.getCartId(), cs.getTerminalId(), "app247://session/" + cs.getSessionId(), cs.getStatus(), cs.getExpiresAt());
    }
}
