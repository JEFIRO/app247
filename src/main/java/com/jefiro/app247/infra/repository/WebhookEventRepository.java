package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, String> {
    boolean existsByEventId(String eventId);
}