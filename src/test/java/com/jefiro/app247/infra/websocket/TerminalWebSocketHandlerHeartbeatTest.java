package com.jefiro.app247.infra.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jefiro.app247.domain.model.dto.TerminalStatusDTO;
import com.jefiro.app247.domain.model.enum_type.TerminalStatus;
import com.jefiro.app247.domain.model.terminal.Terminal;
import com.jefiro.app247.infra.service.TerminalService;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TerminalWebSocketHandlerHeartbeatTest {
    @Test
    void respondeAckSomenteDepoisDoServiceRetornarLastPing() throws Exception {
        TerminalWebSocketHandler handler = new TerminalWebSocketHandler();
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        TerminalService service = mock(TerminalService.class);
        inject(handler, "objectMapper", mapper);
        inject(handler, "service", service);

        Terminal terminal = new Terminal();
        terminal.setIdTerminal("terminal-a");
        terminal.setStatus(TerminalStatus.ONLINE);
        terminal.setLastPing(LocalDateTime.of(2026, 8, 24, 15, 0));
        when(service.updateStatus(any(TerminalStatusDTO.class))).thenReturn(terminal);

        WebSocketSession session = mock(WebSocketSession.class);
        handler.handleTextMessage(session,
                new TextMessage("{\"terminalId\":\"terminal-a\",\"status\":\"ONLINE\"}"));

        var message = org.mockito.ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(message.capture());
        assertThat(message.getValue().getPayload()).contains(
                "\"type\":\"HEARTBEAT_ACK\"",
                "\"terminalId\":\"terminal-a\"",
                "\"lastPing\":\"2026-08-24T15:00\"");
    }

    private static void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
