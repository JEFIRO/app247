package com.jefiro.app247.infra.dto.mercadopago;

import java.time.Instant;
import com.jefiro.app247.domain.model.enum_type.MercadoPagoSetupState;

public record MercadoPagoSetupStatusResponse(
        boolean contaVinculada,
        boolean maquininhaVinculada,
        boolean configuracaoCompleta,
        long quantidadeTerminais,
        long quantidadeMaquininhasVinculadas,
        Instant dataVinculacao,
        MercadoPagoSetupState contaStatus
) {
    public MercadoPagoSetupStatusResponse(boolean contaVinculada, boolean maquininhaVinculada,
                                          boolean configuracaoCompleta, long quantidadeTerminais,
                                          long quantidadeMaquininhasVinculadas, Instant dataVinculacao) {
        this(contaVinculada, maquininhaVinculada, configuracaoCompleta, quantidadeTerminais,
                quantidadeMaquininhasVinculadas, dataVinculacao,
                contaVinculada
                        ? (maquininhaVinculada ? MercadoPagoSetupState.POINT_CONFIGURADA
                        : MercadoPagoSetupState.CONTA_VINCULADA_SEM_POINT)
                        : MercadoPagoSetupState.CONTA_NAO_VINCULADA);
    }
}
