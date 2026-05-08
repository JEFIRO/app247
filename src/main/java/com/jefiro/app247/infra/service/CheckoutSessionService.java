package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.dto.CheckoutSession;
import com.jefiro.app247.domain.model.enum_type.SessionStatus;
import com.jefiro.app247.infra.repository.CheckoutSessionRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class CheckoutSessionService {

    private final CheckoutSessionRepository repository;

    public CheckoutSessionService(CheckoutSessionRepository repository) {
        this.repository = repository;
    }

    public CheckoutSession create(String terminalId, String cartId) {

        CheckoutSession session = new CheckoutSession();

        session.setSessionId(UUID.randomUUID().toString());
        session.setTerminalId(terminalId);
        session.setCartId(cartId);
        session.setStatus(SessionStatus.OPEN);

        session.setCreatedAt(Instant.now());
        session.setExpiresAt(Instant.now().plus(Duration.ofMinutes(10)));

        repository.save(session);

        return session;
    }
}