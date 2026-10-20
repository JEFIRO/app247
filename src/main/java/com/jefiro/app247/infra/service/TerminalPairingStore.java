package com.jefiro.app247.infra.service;

import java.time.Duration;
import java.util.Optional;

/**
 * Sessões de pareamento de vida curta. {@code sessionId} é o hash do pairingToken do
 * equipamento; o token bruto nunca é armazenado.
 */
public interface TerminalPairingStore {

    record Session(String sessionId, String code, String serialNumber, String macAddress,
                   String ipAddress, String terminalId) {
        public boolean approved() {
            return terminalId != null && !terminalId.isBlank();
        }
    }

    /** Grava a sessão e reserva o código; false se o código já estiver em uso (gerar outro). */
    boolean create(Session session, Duration ttl);

    Optional<Session> find(String sessionId);

    Optional<String> sessionIdForCode(String code);

    /**
     * Liga a sessão ao Terminal aprovado e libera o código (uso único).
     *
     * @return false se a sessão expirou nesse meio-tempo
     */
    boolean approve(Session session, String terminalId);

    /** Remove a sessão; true só para quem efetivamente a removeu (entrega única). */
    boolean consume(String sessionId);
}
