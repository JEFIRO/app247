package com.jefiro.app247.domain.model.dto;

import java.time.Instant;

/**
 * {@code pairingToken} é o segredo do equipamento: só ele busca a credencial e nunca deve
 * aparecer na tela. {@code code} e {@code qrPayload} são o que o admin digita ou escaneia.
 */
public record TerminalPairingStartResponse(String pairingToken, String code, String qrPayload,
                                           Instant expiresAt) {
}
