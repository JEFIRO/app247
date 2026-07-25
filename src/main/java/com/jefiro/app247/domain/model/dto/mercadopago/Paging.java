package com.jefiro.app247.domain.model.dto.mercadopago;

public record Paging(
        Integer total,
        Integer limit,
        Integer offset
) {
}
