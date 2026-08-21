package com.jefiro.app247.infra.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jefiro.app247.domain.model.dto.PointPaymentResponse;
import com.jefiro.app247.domain.model.enum_type.TerminalPaymentStatus;
import com.jefiro.app247.domain.model.enum_type.order.OrderStatus;
import com.jefiro.app247.infra.event.PaymentEvent;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentWebSocketHandlerTest {
    @Test
    void enviaSomenteParaTerminalResponsavel() throws Exception {
        PaymentWebSocketHandler handler = new PaymentWebSocketHandler();
        var field = PaymentWebSocketHandler.class.getDeclaredField("objectMapper");
        field.setAccessible(true);
        field.set(handler, new ObjectMapper());
        WebSocketSession correto = session("terminal-a", true);
        WebSocketSession outro = session("terminal-b", true);
        handler.afterConnectionEstablished(correto);
        handler.afterConnectionEstablished(outro);

        handler.sendToTerminal(event("terminal-a"));

        verify(correto).sendMessage(any(TextMessage.class));
        verify(outro, never()).sendMessage(any());
    }

    @Test
    void terminalDesconectadoNaoInterrompeProcessamento() throws Exception {
        PaymentWebSocketHandler handler = new PaymentWebSocketHandler();
        var field = PaymentWebSocketHandler.class.getDeclaredField("objectMapper");
        field.setAccessible(true);
        field.set(handler, new ObjectMapper());

        handler.sendToTerminal(event("terminal-offline"));
    }

    private WebSocketSession session(String terminalId, boolean open) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getUri()).thenReturn(URI.create("ws://localhost/payment-socket/" + terminalId));
        when(session.isOpen()).thenReturn(open);
        return session;
    }

    private PaymentEvent event(String terminalId) {
        return new PaymentEvent(new PointPaymentResponse(
                "PAYMENT_STATUS", "order-1", terminalId, TerminalPaymentStatus.APPROVED,
                OrderStatus.PROCESSED, "payment-1", "accredited", "Pagamento aprovado"));
    }
}
