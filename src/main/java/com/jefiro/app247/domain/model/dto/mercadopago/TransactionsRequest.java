package com.jefiro.app247.domain.model.dto.mercadopago;

import java.util.List;

public record TransactionsRequest(
        List<PaymentRequest> payments
) {
}
