package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.dto.CheckoutSession;

import java.util.Optional;

public interface CheckoutSessionRepository {
    void save(CheckoutSession session);
    Optional<CheckoutSession> findById(String id);
    void delete(String id);
}