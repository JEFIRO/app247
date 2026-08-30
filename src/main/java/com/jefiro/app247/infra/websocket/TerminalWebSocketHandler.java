package com.jefiro.app247.infra.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jefiro.app247.domain.model.dto.TerminalStatusDTO;
import com.jefiro.app247.infra.service.TerminalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.LinkedHashMap;

@Component
public class TerminalWebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    TerminalService service;

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {

        TerminalStatusDTO dto =
                objectMapper.readValue(
                        message.getPayload(),
                        TerminalStatusDTO.class
        );
        var terminal = service.updateStatus(dto);
        var acknowledgement = new LinkedHashMap<String, Object>();
        acknowledgement.put("type", "HEARTBEAT_ACK");
        acknowledgement.put("terminalId", terminal.getIdTerminal());
        acknowledgement.put("status", terminal.getStatus().name());
        acknowledgement.put("lastPing", terminal.getLastPing().toString());
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(acknowledgement)));
    }
}
