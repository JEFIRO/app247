package com.jefiro.app247.domain.model.dto;

import com.jefiro.app247.domain.model.Carrinho;
import com.jefiro.app247.domain.model.enum_type.SessionStatus;
import lombok.Data;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;


@Data
public class CheckoutSession {

    private String sessionId;
    private String cartId;
    private String terminalId;
    private SessionStatus status;
    private String userId;

    private Instant createdAt;
    private Instant expiresAt;

    public CheckoutSession(Carrinho carrinho) {
        this.sessionId = UUID.randomUUID().toString();
        this.cartId = carrinho.getCarrinhoId();
        this.terminalId = carrinho.getTerminalId();
        this.status = SessionStatus.OPEN;

        this.createdAt = Instant.now();
        this.expiresAt = Instant.now().plus(Duration.ofMinutes(15));
    }
}
