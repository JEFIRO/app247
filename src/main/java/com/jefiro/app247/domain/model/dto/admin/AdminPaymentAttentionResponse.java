package com.jefiro.app247.domain.model.dto.admin;

import java.time.Instant;

public record AdminPaymentAttentionResponse(
        long precisamAtencao,
        Instant pendenteAntesDe
) {
}
