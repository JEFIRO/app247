package com.jefiro.app247.infra.service.comprovante;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FastApiComprovanteResponse(
        String status,
        String pedido,
        String destinatario,
        @JsonProperty("n8n_status") Integer n8nStatus
) {
}
