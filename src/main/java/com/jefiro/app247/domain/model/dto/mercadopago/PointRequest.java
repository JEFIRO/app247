package com.jefiro.app247.domain.model.dto.mercadopago;

public record PointRequest(
        String terminal_id,
        String print_on_terminal
) {
}