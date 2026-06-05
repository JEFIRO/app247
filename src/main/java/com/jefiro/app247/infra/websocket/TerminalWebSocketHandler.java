package com.jefiro.app247.infra.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jefiro.app247.domain.model.dto.TerminalStatusDTO;
import com.jefiro.app247.infra.service.TerminalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class TerminalWebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    TerminalService service;

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws JsonProcessingException {

        TerminalStatusDTO dto =
                objectMapper.readValue(
                        message.getPayload(),
                        TerminalStatusDTO.class
                );
        service.updateStatus(dto);
        System.out.println(

        );
    }
}