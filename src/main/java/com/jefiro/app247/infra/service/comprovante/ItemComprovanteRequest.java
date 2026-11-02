package com.jefiro.app247.infra.service.comprovante;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record ItemComprovanteRequest(
        String nome,
        BigDecimal quantidade,
        String valor,
        @JsonProperty("valor_unitario") String valorUnitario
) {
}
