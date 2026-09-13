package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.terminal.Terminal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TerminalPresenceServiceTest {
    @Mock RedisTemplate<String, Object> redis;
    @Mock ValueOperations<String, Object> values;

    @Test
    void heartbeatRenovaTtlNoRedis() {
        when(redis.opsForValue()).thenReturn(values);
        var service = service();
        service.recordHeartbeat("terminal-a");
        verify(values).set("terminal:online:terminal-a", "ONLINE", Duration.ofSeconds(75));
    }

    @Test
    void lastPingEhFallbackQuandoRedisNaoTemPresenca() {
        when(redis.hasKey(anyString())).thenThrow(new RuntimeException("redis offline"));
        var service = service();
        var terminal = new Terminal();
        terminal.setIdTerminal("terminal-a");
        terminal.setLastPing(Instant.now().minusSeconds(20));
        assertThat(service.isOnline(terminal)).isTrue();
        terminal.setLastPing(Instant.now().minusSeconds(61));
        assertThat(service.isOnline(terminal)).isFalse();
    }

    private TerminalPresenceService service() {
        return new TerminalPresenceService(redis, new TelemetryThresholds(
                70, 80, 80, 90, 90, 35, 500, 1500, 900, 60));
    }
}
