package com.jefiro.app247.infra.service;

record MercadoPagoOrderState(
        String externalReference,
        String status,
        String orderStatusDetail,
        String paymentStatusDetail,
        String paymentId,
        String paymentMethodId,
        String paymentMethodType,
        Integer installments,
        String mercadoPagoOrderId,
        Integer version,
        String eventDate
) {
}
