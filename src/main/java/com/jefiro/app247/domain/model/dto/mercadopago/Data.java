package com.jefiro.app247.domain.model.dto.mercadopago;

import java.util.List;

public record Data(
        List<TerminalResponse> terminals
) {
}