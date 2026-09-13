package com.jefiro.app247.infra.service.comprovante;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FastApiComprovanteResponse(
        boolean success,
        String pedido,
        String canal,
        @JsonProperty("request_id") String requestId,
        String status
) {
}
