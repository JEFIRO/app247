package com.jefiro.app247.infra.service.comprovante;

public record ComprovanteEnvioResponse(
        String status,
        String pedido,
        String tipoEnvio,
        Integer n8nStatus,
        boolean duplicado
) {
    static ComprovanteEnvioResponse sent(String pedido, String tipoEnvio, Integer n8nStatus) {
        return new ComprovanteEnvioResponse("ENVIADO", pedido, tipoEnvio, n8nStatus, false);
    }

    static ComprovanteEnvioResponse duplicate(String pedido, String tipoEnvio, Integer n8nStatus) {
        return new ComprovanteEnvioResponse("JA_ENVIADO", pedido, tipoEnvio, n8nStatus, true);
    }
}
