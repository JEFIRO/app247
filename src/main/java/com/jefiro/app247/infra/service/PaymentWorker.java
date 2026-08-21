package com.jefiro.app247.infra.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

@Service
public class PaymentWorker {
    private static final Logger log = LoggerFactory.getLogger(PaymentWorker.class);
    static final String QUEUE = "mp_queue";
    static final String PROCESSING_QUEUE = "mp_queue:processing";
    static final String DEAD_LETTER_QUEUE = "mp_queue:dlq";
    static final int MAX_ATTEMPTS = 3;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private PagamentoService pagamentoService;

    @Scheduled(fixedDelay = 2000)
    public void processQueue() {
        String json = redisTemplate.opsForList().rightPopAndLeftPush(QUEUE, PROCESSING_QUEUE);
        if (json == null || json.isBlank()) {
            return; 
        }

        try {
            pagamentoService.atualizarPagamento(json);
            redisTemplate.opsForList().remove(PROCESSING_QUEUE, 1, json);
            redisTemplate.delete(retryKey(json));
        } catch (Exception e) {
            String retryKey = retryKey(json);
            Long attempts = redisTemplate.opsForValue().increment(retryKey);
            redisTemplate.expire(retryKey, Duration.ofHours(24));
            redisTemplate.opsForList().remove(PROCESSING_QUEUE, 1, json);
            if (attempts != null && attempts >= MAX_ATTEMPTS) {
                redisTemplate.opsForList().leftPush(DEAD_LETTER_QUEUE, json);
                redisTemplate.delete(retryKey);
                log.error("Webhook Mercado Pago enviado para DLQ após {} tentativas: errorType={}",
                        attempts, e.getClass().getSimpleName());
            } else {
                redisTemplate.opsForList().leftPush(QUEUE, json);
                log.warn("Falha ao processar webhook Mercado Pago; tentativa={}", attempts, e);
            }
        }
    }

    private String retryKey(String json) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(json.getBytes(StandardCharsets.UTF_8));
            return "mp_queue:retry:" + java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
