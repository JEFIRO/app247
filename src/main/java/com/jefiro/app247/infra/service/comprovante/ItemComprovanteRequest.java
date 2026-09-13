package com.jefiro.app247.infra.service.comprovante;

public record ItemComprovanteRequest(
        String nome,
        Integer quantidade,
        String valor
) {
}
