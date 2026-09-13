package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.dto.OrderResponse;

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
    static MercadoPagoOrderState from(OrderResponse response) {
        OrderResponse.Payment payment = response.transactions() != null
                && response.transactions().payments() != null
                && !response.transactions().payments().isEmpty()
                ? response.transactions().payments().get(0) : null;
        return new MercadoPagoOrderState(
                response.externalReference(), response.status(), response.statusDetail(),
                payment != null && payment.statusDetail() != null
                        ? payment.statusDetail() : response.statusDetail(),
                payment != null ? payment.id() : null,
                payment != null && payment.paymentMethod() != null ? payment.paymentMethod().id() : null,
                payment != null && payment.paymentMethod() != null ? payment.paymentMethod().type() : null,
                payment != null && payment.paymentMethod() != null ? payment.paymentMethod().installments() : null,
                response.id(), response.version(), response.lastUpdatedDate());
    }
}
