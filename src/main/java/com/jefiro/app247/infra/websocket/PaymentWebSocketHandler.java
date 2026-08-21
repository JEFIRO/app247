package com.jefiro.app247.infra.websocket;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.jefiro.app247.infra.event.PaymentEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PaymentWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(PaymentWebSocketHandler.class);
    @Autowired
    private ObjectMapper objectMapper;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {

        String path = session.getUri().getPath();

        String terminalId = path.substring(path.lastIndexOf("/") + 1);

        sessions.put(terminalId, session);

        log.info("Terminal conectado ao canal de pagamento: terminalId={}", terminalId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {

        sessions.values().remove(session);
    }


    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void sendToTerminal(PaymentEvent event) {

        WebSocketSession session = sessions.get(event.getTerminalId());

        if (session != null && session.isOpen()) {
            try {
                String json = objectMapper.writeValueAsString(event);
                session.sendMessage(new TextMessage(json));
                log.info("Resultado de pagamento enviado ao terminal: terminalId={}, orderId={}, status={}",
                        event.getTerminalId(), event.getOrderId(), event.getStatus());
            } catch (IOException e) {
                log.warn("Falha ao enviar resultado ao terminal: terminalId={}, orderId={}",
                        event.getTerminalId(), event.getOrderId(), e);
            }
        } else {
            log.info("Terminal sem WebSocket ativo; status permanece consultável: terminalId={}, orderId={}, status={}",
                    event.getTerminalId(), event.getOrderId(), event.getStatus());
        }
    }
}
