package com.jefiro.app247.domain.model.dto;

import java.math.BigDecimal;

public record ItemRequest(
        String productId,
        Integer quantity,
        BigDecimal receivedWeight

) {
}
