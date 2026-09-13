package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.terminal.Terminal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class TerminalPresenceService {
    private static final Logger log = LoggerFactory.getLogger(TerminalPresenceService.class);
    private final RedisTemplate<String, Object> redisTemplate;
    private final TelemetryThresholds thresholds;

    public TerminalPresenceService(RedisTemplate<String, Object> redisTemplate,
                                   TelemetryThresholds thresholds) {
        this.redisTemplate = redisTemplate;
        this.thresholds = thresholds;
    }

    public void recordHeartbeat(String terminalId) {
        try {
            redisTemplate.opsForValue().set(key(terminalId), "ONLINE",
                    Duration.ofSeconds(thresholds.getOfflineSeconds() + 15));
        } catch (RuntimeException error) {
            log.warn("[TERMINAL-PRESENCE] Redis indisponível; usando lastPing terminal={}", terminalId);
        }
    }

    public boolean isOnline(Terminal terminal) {
        try {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(key(terminal.getIdTerminal())))) return true;
        } catch (RuntimeException ignored) {
            // lastPing é o fallback durável quando Redis não está disponível.
        }
        return terminal.getLastPing() != null && terminal.getLastPing().isAfter(
                Instant.now().minusSeconds(thresholds.getOfflineSeconds()));
    }

    private String key(String terminalId) { return "terminal:online:" + terminalId; }
}
