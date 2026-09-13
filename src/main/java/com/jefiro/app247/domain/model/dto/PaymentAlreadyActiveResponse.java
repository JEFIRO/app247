package com.jefiro.app247.domain.model.dto;

import com.jefiro.app247.infra.exception.PaymentAlreadyActiveException;
import org.springframework.http.HttpStatus;

public record PaymentAlreadyActiveResponse(
        HttpStatus status,
        String message,
        String code,
        String orderId,
        String paymentAttemptId,
        String cartId,
        String paymentStatus
) {
    public static PaymentAlreadyActiveResponse from(PaymentAlreadyActiveException exception) {
        return new PaymentAlreadyActiveResponse(
                HttpStatus.CONFLICT,
                exception.getReason(),
                "PAYMENT_ALREADY_ACTIVE",
                exception.getOrderId(),
                exception.getPaymentAttemptId(),
                exception.getCartId(),
                exception.getPaymentStatus()
        );
    }
}
