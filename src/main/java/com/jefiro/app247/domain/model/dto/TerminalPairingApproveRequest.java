package com.jefiro.app247.domain.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Código mostrado na tela do Terminal, digitado ou lido do QR (aceita o payload inteiro do QR). */
public record TerminalPairingApproveRequest(
        @NotBlank(message = "Código de pareamento é obrigatório")
        @Size(max = 64, message = "Código de pareamento inválido")
        String code
) {
}
