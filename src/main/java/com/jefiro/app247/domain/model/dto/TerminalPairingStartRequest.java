package com.jefiro.app247.domain.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Terminal sem credencial pedindo um código de pareamento (rota pública). */
public record TerminalPairingStartRequest(
        @NotBlank(message = "Número de série é obrigatório")
        @Size(max = 120, message = "Número de série deve ter no máximo 120 caracteres")
        String serialNumber,
        @Size(max = 30, message = "MAC deve ter no máximo 30 caracteres")
        String macAddress,
        @Size(max = 45, message = "IP deve ter no máximo 45 caracteres")
        String ipAddress
) {
}
