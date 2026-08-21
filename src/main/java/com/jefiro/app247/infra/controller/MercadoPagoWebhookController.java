package com.jefiro.app247.infra.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jefiro.app247.infra.service.MercadoPagoWebhookSignatureService;
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

    public MercadoPagoWebhookController(
            RedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper,
            MercadoPagoWebhookSignatureService signatureService
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.signatureService = signatureService;
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

        String deduplicationKey = "mp_webhook:" + action + ':' + bodyDataId + ':' + version;
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

        String bodyJson = objectMapper.writeValueAsString(body);
        try {
            redisTemplate.opsForList().leftPush("mp_queue", bodyJson);
            log.info("Webhook Point recebido e enfileirado: action={}, mpOrderId={}, version={}",
                    action, bodyDataId, version);
        } catch (RuntimeException e) {
            redisTemplate.delete(deduplicationKey);
            throw e;
        }

        return ResponseEntity.ok("ENFILEIRADO");
    }
}
