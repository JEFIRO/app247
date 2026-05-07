package com.jefiro.app247.domain.model.dto;

import java.math.BigDecimal;

public record ItemRequest(
        Long productId,
        Integer quantity,
        BigDecimal receivedWeight

) {
}
