package com.jefiro.app247.domain.model.dto;

import com.jefiro.app247.domain.model.enum_type.SessionStatus;
import lombok.Data;

import java.time.Instant;


@Data
public class CheckoutSession {

    private String sessionId;
    private String cartId;
    private String userId;
    private String terminalId;

    private SessionStatus status;

    private Instant createdAt;
    private Instant expiresAt;
}
