package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.dto.ValidateCodeRequest;

import com.jefiro.app247.domain.model.dto.ValidateEmailRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

@Service
public class EmailWorker {
    private static final Logger log = LoggerFactory.getLogger(EmailWorker.class);
    private static final int MAX_ATTEMPTS = 3;
    @Autowired
    RedisTemplate<String, Object> redisTemplate;
    @Autowired
    EmailService sender;

    @Scheduled(fixedDelay = 2000)
    public void processQueue() {
        process("recovery_queue", ValidateCodeRequest.class,
                request -> sender.enviarEmail(request));
    }

    @Scheduled(fixedDelay = 2000)
    public void sender() {
        process("email_validation_queue", ValidateEmailRequest.class,
                request -> sender.enviarEmail(request));
    }

    private <T> void process(String queue, Class<T> type, ThrowingConsumer<T> action) {
        String processingQueue = queue + ":processing";
        String deadLetterQueue = queue + ":dlq";
        Object raw = redisTemplate.opsForList().rightPopAndLeftPush(queue, processingQueue);
        if (raw == null) return;

        if (!type.isInstance(raw)) {
            redisTemplate.opsForList().remove(processingQueue, 1, raw);
            redisTemplate.opsForList().leftPush(deadLetterQueue, raw);
            log.error("Mensagem de tipo inválido enviada para DLQ: queue={}, type={}",
                    queue, raw.getClass().getName());
            return;
        }

        String retryKey = queue + ":retry:" + Integer.toUnsignedString(raw.hashCode(), 16);
        try {
            action.accept(type.cast(raw));
            redisTemplate.opsForList().remove(processingQueue, 1, raw);
            redisTemplate.delete(retryKey);
        } catch (Exception e) {
            Long attempts = redisTemplate.opsForValue().increment(retryKey);
            redisTemplate.expire(retryKey, Duration.ofHours(24));
            redisTemplate.opsForList().remove(processingQueue, 1, raw);
            if (attempts != null && attempts >= MAX_ATTEMPTS) {
                redisTemplate.opsForList().leftPush(deadLetterQueue, raw);
                redisTemplate.delete(retryKey);
                log.error("Mensagem de e-mail enviada para DLQ: queue={}, attempts={}", queue, attempts);
            } else {
                redisTemplate.opsForList().leftPush(queue, raw);
                log.warn("Falha temporária no envio de e-mail: queue={}, attempt={}", queue, attempts, e);
            }
        }
    }

    @FunctionalInterface
    private interface ThrowingConsumer<T> {
        void accept(T value) throws Exception;
    }
}
