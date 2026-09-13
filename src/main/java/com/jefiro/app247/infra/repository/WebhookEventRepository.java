package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.WebhookEvent;
import com.jefiro.app247.domain.model.enum_type.PaymentProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, String> {
    Optional<WebhookEvent> findByProviderAndEventId(PaymentProvider provider, String eventId);
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from WebhookEvent w where w.processedAt is not null and w.processedAt<:cutoff")
    int deleteProcessedBefore(java.time.Instant cutoff);
}
