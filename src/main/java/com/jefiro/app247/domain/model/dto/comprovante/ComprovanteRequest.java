package com.jefiro.app247.domain.model.dto.comprovante;

import jakarta.validation.constraints.NotBlank;

public record ComprovanteRequest(
        @NotBlank(message = "terminalId é obrigatório")
        String terminalId,
        @NotBlank(message = "pedidoId é obrigatório")
        String pedidoId,
        @NotBlank(message = "tipoEnvio é obrigatório")
        String tipoEnvio,
        @NotBlank(message = "destinatario é obrigatório")
        String destinatario
) {
}
