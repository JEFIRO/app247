package com.jefiro.app247.infra.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jefiro.app247.domain.model.dto.PointPaymentResponse;
import com.jefiro.app247.domain.model.enum_type.TerminalPaymentStatus;
import com.jefiro.app247.domain.model.enum_type.order.OrderStatus;
import com.jefiro.app247.infra.event.PaymentEvent;
import com.jefiro.app247.infra.event.TerminalFactoryResetRequiredEvent;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentWebSocketHandlerTest {
    @Test
    void enviaSomenteParaTerminalResponsavel() throws Exception {
        PaymentWebSocketHandler handler = new PaymentWebSocketHandler();
        var field = PaymentWebSocketHandler.class.getDeclaredField("objectMapper");
        field.setAccessible(true);
        field.set(handler, new ObjectMapper().findAndRegisterModules());
        WebSocketSession correto = session("terminal-a", true);
        WebSocketSession outro = session("terminal-b", true);
        handler.afterConnectionEstablished(correto);
        handler.afterConnectionEstablished(outro);

        handler.sendToTerminal(event("terminal-a"));

        var captor = org.mockito.ArgumentCaptor.forClass(TextMessage.class);
        verify(correto).sendMessage(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getPayload())
                .contains("\"paymentAttemptId\":\"attempt-1\"");
        verify(outro, never()).sendMessage(any());
    }

    @Test
    void terminalDesconectadoNaoInterrompeProcessamento() throws Exception {
        PaymentWebSocketHandler handler = new PaymentWebSocketHandler();
        var field = PaymentWebSocketHandler.class.getDeclaredField("objectMapper");
        field.setAccessible(true);
        field.set(handler, new ObjectMapper().findAndRegisterModules());

        handler.sendToTerminal(event("terminal-offline"));
    }

    @Test
    void enviaResetImediatoAoTerminalOnline() throws Exception {
        PaymentWebSocketHandler handler = new PaymentWebSocketHandler();
        var field = PaymentWebSocketHandler.class.getDeclaredField("objectMapper");
        field.setAccessible(true);
        field.set(handler, new ObjectMapper().findAndRegisterModules());
        WebSocketSession terminal = session("terminal-a", true);
        handler.afterConnectionEstablished(terminal);

        handler.sendFactoryResetToTerminal(new TerminalFactoryResetRequiredEvent(
                "terminal-a", "COMPANY_CLOSED", Instant.parse("2026-09-05T20:00:00Z")));

        var captor = org.mockito.ArgumentCaptor.forClass(TextMessage.class);
        verify(terminal).sendMessage(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getPayload())
                .contains("\"type\":\"TERMINAL_FACTORY_RESET_REQUIRED\"")
                .contains("\"state\":\"RESET_REQUIRED\"")
                .contains("\"reason\":\"COMPANY_CLOSED\"");
    }

    private WebSocketSession session(String terminalId, boolean open) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getUri()).thenReturn(URI.create("ws://localhost/payment-socket/" + terminalId));
        when(session.isOpen()).thenReturn(open);
        return session;
    }

    private PaymentEvent event(String terminalId) {
        return new PaymentEvent(new PointPaymentResponse(
                "PAYMENT_STATUS", "order-1", "attempt-1", terminalId, TerminalPaymentStatus.APPROVED,
                OrderStatus.PROCESSED, "payment-1", "accredited", "Pagamento aprovado"));
    }
}
