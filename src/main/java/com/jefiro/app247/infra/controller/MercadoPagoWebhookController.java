package com.jefiro.app247.infra.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jefiro.app247.infra.service.MercadoPagoWebhookSignatureService;
import com.jefiro.app247.infra.service.WebhookInboxService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/webhook")
public class MercadoPagoWebhookController {
    private static final Logger log = LoggerFactory.getLogger(MercadoPagoWebhookController.class);

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final MercadoPagoWebhookSignatureService signatureService;
    private final WebhookInboxService inboxService;

    public MercadoPagoWebhookController(
            RedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper,
            MercadoPagoWebhookSignatureService signatureService,
            WebhookInboxService inboxService
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.signatureService = signatureService;
        this.inboxService = inboxService;
    }


    @PostMapping("/mercadopago")
    public ResponseEntity<String> receive(
            @RequestHeader(value = "x-signature", required = false) String xSignature,
            @RequestHeader(value = "x-request-id", required = false) String xRequestId,
            @RequestParam(value = "data.id", required = false) String dataId,
            @RequestBody Map<String, Object> body
    ) throws JsonProcessingException {
        if (!signatureService.isConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Validação do webhook não configurada");
        }
        if (!signatureService.isValid(xSignature, xRequestId, dataId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Assinatura inválida");
        }

        Object dataObject = body.get("data");
        if (!(dataObject instanceof Map<?, ?> data)) {
            return ResponseEntity.badRequest().body("Payload sem campo 'data'");
        }

        String bodyDataId = data.get("id") instanceof String id ? id : dataId;
        String action = body.get("action") instanceof String value ? value : "unknown";
        String version = data.get("version") != null ? data.get("version").toString() : "unknown";
        if (bodyDataId == null || bodyDataId.isBlank()) {
            return ResponseEntity.badRequest().body("Payload sem data.id");
        }
        if (dataId != null && !dataId.equalsIgnoreCase(bodyDataId)) {
            return ResponseEntity.badRequest().body("data.id da URL difere do payload");
        }

        Integer numericVersion = null;
        if (!"unknown".equals(version)) {
            try {
                numericVersion = Integer.valueOf(version);
            } catch (NumberFormatException ignored) {
                return ResponseEntity.badRequest().body("Versão do webhook inválida");
            }
        }
        String bodyJson = objectMapper.writeValueAsString(body);
        WebhookInboxService.Registration registration = inboxService.register(
                action, bodyDataId, numericVersion, bodyJson);
        if (!registration.created()
                && !"RECEIVED".equals(registration.event().getProcessingStatus())) {
            log.info("Webhook Point duplicado no inbox: action={}, mpOrderId={}, version={}",
                    action, bodyDataId, version);
            return ResponseEntity.ok("DUPLICADO");
        }

        String deduplicationKey = "mp_webhook:" + registration.event().getEventId();
        Boolean firstReceipt = redisTemplate.opsForValue().setIfAbsent(
                deduplicationKey,
                "received",
                Duration.ofHours(24)
        );
        if (!Boolean.TRUE.equals(firstReceipt)) {
            log.info("Webhook Point duplicado confirmado: action={}, mpOrderId={}, version={}",
                    action, bodyDataId, version);
            return ResponseEntity.ok("DUPLICADO");
        }

        try {
            redisTemplate.opsForList().leftPush("mp_queue", bodyJson);
            inboxService.markQueued(registration.event().getEventId());
            log.info("Webhook Point recebido e enfileirado: action={}, mpOrderId={}, version={}",
                    action, bodyDataId, version);
        } catch (RuntimeException e) {
            redisTemplate.delete(deduplicationKey);
            throw e;
        }

        return ResponseEntity.ok("ENFILEIRADO");
    }
}
