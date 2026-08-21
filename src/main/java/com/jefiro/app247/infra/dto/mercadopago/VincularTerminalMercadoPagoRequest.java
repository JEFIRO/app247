package com.jefiro.app247.infra.dto.mercadopago;

import jakarta.validation.constraints.NotBlank;

public record VincularTerminalMercadoPagoRequest(
        @NotBlank String mercadoPagoTerminalId
) {
}
