package com.jefiro.app247.infra.websocket;


import com.jefiro.app247.infra.event.PaymentEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PaymentWebSocketHandler extends TextWebSocketHandler {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {

        String path = session.getUri().getPath();

        String terminalId = path.substring(path.lastIndexOf("/") + 1);

        sessions.put(terminalId, session);

        System.out.println("Terminal conectado: " + terminalId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {

        sessions.values().remove(session);
    }

    @Async
    @EventListener
    public void sendToTerminal(PaymentEvent event) throws IOException {

        WebSocketSession session = sessions.get(event.getTerminalId());

        if (session != null && session.isOpen()) {

            session.sendMessage(new TextMessage(event.toString()));
        }
    }
}