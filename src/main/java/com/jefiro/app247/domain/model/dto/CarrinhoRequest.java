package com.jefiro.app247.domain.model.dto;

import java.util.List;

public record CarrinhoRequest(
        String terminalId,
        List<ItemRequest> items

) {
}