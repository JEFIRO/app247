package com.jefiro.app247.infra.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jefiro.app247.domain.model.PaymentAttempt;
import com.jefiro.app247.domain.model.WebhookEvent;
import com.jefiro.app247.domain.model.enum_type.PaymentProvider;
import com.jefiro.app247.infra.repository.PagamentoRepository;
import com.jefiro.app247.infra.repository.WebhookEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Inbox persistente para deduplicação e auditoria de webhooks. Redis continua sendo apenas a fila rápida. */
@Service
public class WebhookInboxService {
    private static final Logger log = LoggerFactory.getLogger(WebhookInboxService.class);
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "authorization", "access_token", "refresh_token", "client_secret", "password", "senha", "jwt");

    private final WebhookEventRepository repository;
    private final PagamentoRepository pagamentoRepository;
    private final ObjectMapper mapper;

    public WebhookInboxService(WebhookEventRepository repository,
                               PagamentoRepository pagamentoRepository,
                               ObjectMapper mapper) {
        this.repository = repository;
        this.pagamentoRepository = pagamentoRepository;
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Registration register(String action, String providerOrderId, Integer version, String payload) {
        String eventId = eventId(action, providerOrderId, version);
        WebhookEvent existing = repository
                .findByProviderAndEventId(PaymentProvider.MERCADO_PAGO, eventId)
                .orElse(null);
        if (existing != null) return new Registration(existing, false);

        WebhookEvent event = new WebhookEvent();
        event.setProvider(PaymentProvider.MERCADO_PAGO);
        event.setEventId(eventId);
        event.setAction(action);
        event.setProviderVersion(version);
        event.setProcessingStatus("RECEIVED");
        event.setPayload(sanitize(payload));
        return new Registration(repository.saveAndFlush(event), true);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markQueued(String eventId) {
        repository.findByProviderAndEventId(PaymentProvider.MERCADO_PAGO, eventId).ifPresent(event -> {
            if (!"PROCESSED".equals(event.getProcessingStatus())) {
                event.setProcessingStatus("QUEUED");
                repository.save(event);
            }
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessedSafely(String payload) {
        updateSafely(payload, "PROCESSED", null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markDeadLetterSafely(String payload, Throwable error) {
        updateSafely(payload, "DLQ", error != null ? error.getClass().getSimpleName() : "UnknownError");
    }

    private void update(String payload, String status, String error) throws Exception {
        Identity identity = identity(payload);
        repository.findByProviderAndEventId(PaymentProvider.MERCADO_PAGO, identity.eventId())
                .ifPresent(event -> {
                    PaymentAttempt attempt = resolveAttempt(identity);
                    if (attempt != null) {
                        event.setPaymentAttempt(attempt);
                        event.setEmpresa(attempt.getEmpresa());
                    }
                    event.setProcessingStatus(status);
                    event.setProcessingError(error);
                    if ("PROCESSED".equals(status)) event.setProcessedAt(Instant.now());
                    repository.save(event);
                });
    }

    private void updateSafely(String payload, String status, String error) {
        try {
            update(payload, status, error);
        } catch (Exception exception) {
            log.warn("Não foi possível atualizar auditoria do webhook: status={} errorType={}",
                    status, exception.getClass().getSimpleName());
        }
    }

    private PaymentAttempt resolveAttempt(Identity identity) {
        if (identity.externalReference() != null && !identity.externalReference().isBlank()) {
            return pagamentoRepository.findByProviderAndExternalReference(
                    PaymentProvider.MERCADO_PAGO, identity.externalReference()).orElse(null);
        }
        return pagamentoRepository.findByProviderOrderId(identity.providerOrderId()).orElse(null);
    }

    private Identity identity(String payload) throws Exception {
        JsonNode root = mapper.readTree(payload);
        JsonNode data = root.path("data");
        String action = root.path("action").asText("unknown");
        String providerOrderId = data.path("id").asText(null);
        Integer version = data.hasNonNull("version") ? data.get("version").asInt() : null;
        String externalReference = data.path("external_reference").asText(null);
        return new Identity(eventId(action, providerOrderId, version), providerOrderId, externalReference);
    }

    static String eventId(String action, String providerOrderId, Integer version) {
        String source = String.valueOf(action) + '|' + providerOrderId + '|' + version;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 indisponível", impossible);
        }
    }

    private String sanitize(String payload) {
        try {
            JsonNode tree = mapper.readTree(payload);
            redact(tree);
            return mapper.writeValueAsString(tree);
        } catch (Exception error) {
            throw new IllegalArgumentException("Payload de webhook inválido", error);
        }
    }

    private void redact(JsonNode node) {
        if (node == null) return;
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = ((ObjectNode) node).fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String key = field.getKey().toLowerCase(Locale.ROOT);
                if (SENSITIVE_KEYS.stream().anyMatch(key::contains)) {
                    ((ObjectNode) node).put(field.getKey(), "[REDACTED]");
                } else {
                    redact(field.getValue());
                }
            }
        } else if (node.isArray()) {
            node.forEach(this::redact);
        }
    }

    public record Registration(WebhookEvent event, boolean created) {}
    private record Identity(String eventId, String providerOrderId, String externalReference) {}
}
