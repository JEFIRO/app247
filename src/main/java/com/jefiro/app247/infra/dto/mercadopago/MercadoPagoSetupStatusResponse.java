package com.jefiro.app247.infra.dto.mercadopago;

public record MercadoPagoSetupStatusResponse(
        boolean contaVinculada,
        boolean maquininhaVinculada,
        boolean configuracaoCompleta,
        long quantidadeTerminais,
        long quantidadeMaquininhasVinculadas
) {
}
