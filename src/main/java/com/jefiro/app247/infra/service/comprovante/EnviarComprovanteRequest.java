package com.jefiro.app247.infra.service.comprovante;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EnviarComprovanteRequest(
        @JsonProperty("request_id") String requestId,
        String canal,
        ComprovanteCompraRequest request,
        String destinatario
) {
}
