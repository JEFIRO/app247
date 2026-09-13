package com.jefiro.app247.infra.websocket;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.jefiro.app247.infra.event.PaymentEvent;
import com.jefiro.app247.infra.event.TerminalFactoryResetRequiredEvent;
import org.springframework.beans.factory.annotation.Autowired;
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

        log.info("[PAYMENT-WS] socket nativo conectado terminalId={} activeSessions={}",
                terminalId, sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {

        sessions.values().remove(session);
        log.info("[PAYMENT-WS] socket nativo desconectado status={} activeSessions={}",
                status, sessions.size());
    }


    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void sendToTerminal(PaymentEvent event) {
        if (sendToTerminal(event.getTerminalId(), event)) {
            log.info("Resultado de pagamento enviado ao terminal: terminalId={}, orderId={}, status={}",
                    event.getTerminalId(), event.getOrderId(), event.getStatus());
        } else {
            log.info("[PAYMENT-WS] terminal sem socket nativo; status permanece consultável terminalId={} orderId={} status={} activeSessions={}",
                    event.getTerminalId(), event.getOrderId(), event.getStatus(), sessions.size());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void sendFactoryResetToTerminal(TerminalFactoryResetRequiredEvent event) {
        if (!sendToTerminal(event.terminalId(), event)) {
            log.info("[TERMINAL-RESET] terminal offline; comando permanece no bootstrap terminalId={}",
                    event.terminalId());
        }
    }

    public boolean sendToTerminal(String terminalId, Object payload) {
        WebSocketSession session = sessions.get(terminalId);
        if (session == null || !session.isOpen()) return false;
        try {
            String json = objectMapper.writeValueAsString(payload);
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
            return true;
        } catch (IOException e) {
            log.warn("Falha ao enviar mensagem ao terminal: terminalId={}", terminalId, e);
            return false;
        }
    }
}
