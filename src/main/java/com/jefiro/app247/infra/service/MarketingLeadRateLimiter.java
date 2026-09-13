package com.jefiro.app247.infra.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MarketingLeadRateLimiter {
    private static final Logger log = LoggerFactory.getLogger(MarketingLeadRateLimiter.class);
    private static final Duration WINDOW = Duration.ofMinutes(30);
    private final Map<String, LocalWindow> fallback = new ConcurrentHashMap<>();

    @Autowired
    private StringRedisTemplate redis;

    public boolean allow(String ip, String email) {
        return allowKey("ip", ip, 30) && allowKey("contact", email == null ? "unknown" : email.toLowerCase(), 6);
    }

    private boolean allowKey(String scope, String value, long maximum) {
        String key = "app247:marketing-lead-rate:" + scope + ":" + hash(value);
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1) redis.expire(key, WINDOW);
            return count == null || count <= maximum;
        } catch (RuntimeException exception) {
            log.warn("Redis indisponível para rate limit de lead; usando limite local");
            return allowLocal(key, maximum);
        }
    }

    private boolean allowLocal(String key, long maximum) {
        long now = System.currentTimeMillis();
        LocalWindow window = fallback.compute(key, (ignored, current) -> {
            if (current == null || current.resetAt() <= now) return new LocalWindow(1, now + WINDOW.toMillis());
            return new LocalWindow(current.count() + 1, current.resetAt());
        });
        return window.count() <= maximum;
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponível", exception);
        }
    }

    private record LocalWindow(long count, long resetAt) {}
}
