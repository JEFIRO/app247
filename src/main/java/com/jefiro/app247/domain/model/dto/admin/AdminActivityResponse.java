package com.jefiro.app247.domain.model.dto.admin;

import java.time.Instant;

public record AdminActivityResponse(
        String id,
        String tipo,
        String titulo,
        String descricao,
        String destino,
        Instant ocorridoEm
) {
}
