package com.jefiro.app247.infra.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Contadores de janela fixa para rotas públicas (login, código de e-mail, onboarding).
 * A identidade (CPF, e-mail, IP) é guardada só como HMAC. Sem Redis, cai para um limite
 * local por instância em vez de liberar tudo.
 */
@Component
public class SecurityRateLimiter {
    private static final Logger log = LoggerFactory.getLogger(SecurityRateLimiter.class);
    private static final String PREFIX = "app247:security-rate:";

    @Autowired private StringRedisTemplate redis;
    @Autowired private CustomerIdentityHashService hashes;
    private final Map<String, LocalWindow> fallback = new ConcurrentHashMap<>();

    /** Conta uma tentativa e diz se ainda está dentro do limite. */
    public boolean tryAcquire(String bucket, String identity, long maximum, Duration window) {
        String key = key(bucket, identity);
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1) redis.expire(key, window);
            return count == null || count <= maximum;
        } catch (RuntimeException unavailable) {
            log.warn("[SECURITY-RATE] Redis indisponível; usando limite local bucket={}", bucket);
            long now = System.currentTimeMillis();
            LocalWindow value = fallback.compute(key, (ignored, current) ->
                    current == null || current.resetAt() <= now
                            ? new LocalWindow(1, now + window.toMillis())
                            : new LocalWindow(current.count() + 1, current.resetAt()));
            return value.count() <= maximum;
        }
    }

    /** Diz se o limite já foi atingido, sem contar uma nova tentativa. */
    public boolean isExhausted(String bucket, String identity, long maximum) {
        String key = key(bucket, identity);
        try {
            String count = redis.opsForValue().get(key);
            return count != null && Long.parseLong(count) >= maximum;
        } catch (RuntimeException unavailable) {
            LocalWindow value = fallback.get(key);
            return value != null && value.resetAt() > System.currentTimeMillis() && value.count() >= maximum;
        }
    }

    public void reset(String bucket, String identity) {
        String key = key(bucket, identity);
        fallback.remove(key);
        try {
            redis.delete(key);
        } catch (RuntimeException unavailable) {
            log.warn("[SECURITY-RATE] Redis indisponível ao limpar bucket={}", bucket);
        }
    }

    private String key(String bucket, String identity) {
        return PREFIX + bucket + ":" + hashes.hash("security-rate:" + bucket,
                identity == null || identity.isBlank() ? "unknown" : identity);
    }

    private record LocalWindow(long count, long resetAt) {}
}
