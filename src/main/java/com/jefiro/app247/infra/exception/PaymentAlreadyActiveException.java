package com.jefiro.app247.infra.exception;

import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.PaymentAttempt;
import com.jefiro.app247.domain.model.mapper.MercadoPagoStatusMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Impede que outro carrinho do mesmo Terminal abra uma segunda cobrança
 * enquanto a anterior ainda pode ser concluída remotamente.
 */
public class PaymentAlreadyActiveException extends ResponseStatusException {
    private final String orderId;
    private final String paymentAttemptId;
    private final String cartId;
    private final String paymentStatus;

    public PaymentAlreadyActiveException(Order activeOrder) {
        super(HttpStatus.CONFLICT, "Já existe um pagamento não resolvido para este Terminal");
        PaymentAttempt attempt = activeOrder.getPagamento();
        this.orderId = activeOrder.getIdOrder();
        this.paymentAttemptId = attempt != null ? attempt.getIdPagamento() : null;
        this.cartId = activeOrder.getCarrinho() != null
                ? activeOrder.getCarrinho().getIdCarrinho() : null;
        this.paymentStatus = MercadoPagoStatusMapper.toTerminalStatus(
                activeOrder.getStatus()).name();
    }

    public String getOrderId() {
        return orderId;
    }

    public String getPaymentAttemptId() {
        return paymentAttemptId;
    }

    public String getCartId() {
        return cartId;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }
}
