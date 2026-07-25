package com.jefiro.app247.domain.model.dto.mercadopago;

public record TerminalResponse(
        String id,
        String pos_id,
        String store,
        String external_pos_id,
        String operation_mode
) {
}
