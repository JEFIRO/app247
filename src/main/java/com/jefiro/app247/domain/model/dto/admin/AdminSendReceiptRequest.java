package com.jefiro.app247.domain.model.dto.admin;

import jakarta.validation.constraints.NotBlank;

public record AdminSendReceiptRequest(
        @NotBlank(message = "tipoEnvio é obrigatório")
        String tipoEnvio,
        @NotBlank(message = "destinatario é obrigatório")
        String destinatario
) {
}
