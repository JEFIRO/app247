package com.jefiro.app247.infra.dto.mercadopago;

import java.time.LocalDateTime;

public record MercadoPagoSetupStatusResponse(
        boolean contaVinculada,
        boolean maquininhaVinculada,
        boolean configuracaoCompleta,
        long quantidadeTerminais,
        long quantidadeMaquininhasVinculadas,
        LocalDateTime dataVinculacao
) {
}
