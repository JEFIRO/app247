package com.jefiro.app247.domain.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TerminalPairingClaimRequest(
        @NotBlank(message = "pairingToken é obrigatório")
        @Size(max = 100, message = "pairingToken inválido")
        String pairingToken
) {
}
