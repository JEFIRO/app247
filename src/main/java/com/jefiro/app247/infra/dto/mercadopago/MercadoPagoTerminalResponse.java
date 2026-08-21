package com.jefiro.app247.infra.dto.mercadopago;

import com.jefiro.app247.domain.model.dto.mercadopago.TerminalResponse;

public record MercadoPagoTerminalResponse(
        String id,
        String posId,
        String store,
        String externalPosId,
        String operationMode,
        boolean vinculado,
        String terminalInternoId
) {
    public MercadoPagoTerminalResponse(TerminalResponse externo, String terminalInternoId) {
        this(externo.id(), externo.pos_id(), externo.store(), externo.external_pos_id(),
                externo.operation_mode(), terminalInternoId != null, terminalInternoId);
    }
}
