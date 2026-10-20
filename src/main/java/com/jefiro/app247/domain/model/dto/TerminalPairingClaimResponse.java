package com.jefiro.app247.domain.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * {@code PENDING}: o admin ainda não confirmou o código. {@code PAIRED}: entrega única da
 * credencial, com os mesmos campos de ativação de {@code GET /terminal/serial/{serial}}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TerminalPairingClaimResponse(
        String status,
        String terminalId,
        String serialNumber,
        Boolean ativo,
        Boolean activated,
        String credential,
        Integer credentialVersion
) {
    public static TerminalPairingClaimResponse pending() {
        return new TerminalPairingClaimResponse("PENDING", null, null, null, null, null, null);
    }

    public static TerminalPairingClaimResponse paired(TerminalActivationResponse activation,
                                                      TerminalCredentialResponse credential) {
        return new TerminalPairingClaimResponse("PAIRED", activation.terminalId(), activation.serialNumber(),
                activation.ativo(), activation.activated(), credential.credential(), credential.version());
    }
}
