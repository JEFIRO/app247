package com.jefiro.app247.infra.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jefiro.app247.domain.model.AuditLog;
import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.infra.repository.AuditLogRepository;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class AuditLogService {
    private static final Set<String> SENSITIVE = Set.of(
            "senha", "password", "token", "authorization", "secret", "jwt", "psk", "access_token", "refresh_token");
    private final AuditLogRepository repository;
    private final ObjectMapper mapper;

    public AuditLogService(AuditLogRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    /** Participa da transação de negócio: rollback não deixa auditoria de uma ação inexistente. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Empresa empresa, String action, String entityType, String entityId,
                       Object before, Object after, Map<String, ?> metadata) {
        AuditLog log = new AuditLog();
        log.setEmpresa(empresa);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication != null ? authentication.getPrincipal() : null;
        if (principal instanceof User user && user.getEmpresa() != null
                && user.getEmpresa().getId().equals(empresa.getId())) {
            log.setActorType("USER");
            log.setActorUser(user);
        } else {
            log.setActorType("SYSTEM");
        }
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setCorrelationId(MDC.get("correlationId"));
        log.setBeforeData(jsonSanitizado(before));
        log.setAfterData(jsonSanitizado(after));
        log.setMetadata(jsonSanitizado(metadata));
        repository.save(log);
    }

    String jsonSanitizado(Object value) {
        if (value == null) return null;
        JsonNode tree = mapper.valueToTree(value);
        redact(tree);
        try {
            return mapper.writeValueAsString(tree);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Não foi possível serializar auditoria", e);
        }
    }

    private void redact(JsonNode node) {
        if (node == null) return;
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String key = field.getKey().toLowerCase(Locale.ROOT);
                if (SENSITIVE.stream().anyMatch(key::contains)) object.put(field.getKey(), "[REDACTED]");
                else redact(field.getValue());
            }
        } else if (node.isArray()) {
            node.forEach(this::redact);
        }
    }
}
