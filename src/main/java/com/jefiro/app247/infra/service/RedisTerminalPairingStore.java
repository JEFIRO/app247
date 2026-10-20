package com.jefiro.app247.infra.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class RedisTerminalPairingStore implements TerminalPairingStore {
    private static final String SESSION = "app247:terminal-pairing:session:";
    private static final String CODE = "app247:terminal-pairing:code:";

    private final StringRedisTemplate redis;

    public RedisTerminalPairingStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean create(Session session, Duration ttl) {
        Boolean reserved = redis.opsForValue().setIfAbsent(CODE + session.code(), session.sessionId(), ttl);
        if (!Boolean.TRUE.equals(reserved)) return false;
        Map<String, String> fields = new HashMap<>();
        fields.put("code", session.code());
        fields.put("serialNumber", session.serialNumber());
        if (session.macAddress() != null) fields.put("macAddress", session.macAddress());
        if (session.ipAddress() != null) fields.put("ipAddress", session.ipAddress());
        redis.opsForHash().putAll(SESSION + session.sessionId(), fields);
        redis.expire(SESSION + session.sessionId(), ttl);
        return true;
    }

    @Override
    public Optional<Session> find(String sessionId) {
        Map<Object, Object> fields = redis.opsForHash().entries(SESSION + sessionId);
        if (fields.isEmpty()) return Optional.empty();
        return Optional.of(new Session(sessionId, text(fields, "code"), text(fields, "serialNumber"),
                text(fields, "macAddress"), text(fields, "ipAddress"), text(fields, "terminalId")));
    }

    @Override
    public Optional<String> sessionIdForCode(String code) {
        return Optional.ofNullable(redis.opsForValue().get(CODE + code));
    }

    @Override
    public boolean approve(Session session, String terminalId) {
        String key = SESSION + session.sessionId();
        redis.opsForHash().put(key, "terminalId", terminalId);
        redis.delete(CODE + session.code());
        // Se a sessão expirou entre a leitura e o HSET, o Redis recriou a chave sem TTL.
        Long ttl = redis.getExpire(key);
        if (ttl == null || ttl < 0) {
            redis.delete(key);
            return false;
        }
        return true;
    }

    @Override
    public boolean consume(String sessionId) {
        return Boolean.TRUE.equals(redis.delete(SESSION + sessionId));
    }

    private static String text(Map<Object, Object> fields, String name) {
        Object value = fields.get(name);
        return value == null ? null : value.toString();
    }
}
