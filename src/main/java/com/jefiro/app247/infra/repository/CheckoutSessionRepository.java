package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.dto.CheckoutSession;

public interface CheckoutSessionRepository {
    void save(CheckoutSession session);
    CheckoutSession findById(String id);
    void delete(String id);
}