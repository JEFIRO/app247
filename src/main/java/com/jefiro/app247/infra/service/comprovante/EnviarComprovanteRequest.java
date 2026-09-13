package com.jefiro.app247.infra.service.comprovante;

public record EnviarComprovanteRequest(
        ComprovanteCompraRequest request,
        String destinatario
) {
}
