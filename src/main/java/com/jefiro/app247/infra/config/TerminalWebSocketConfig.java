package com.jefiro.app247.infra.config;

import com.jefiro.app247.infra.websocket.PaymentWebSocketHandler;
import com.jefiro.app247.infra.websocket.TerminalWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class TerminalWebSocketConfig implements WebSocketConfigurer {
    @Autowired
    PaymentWebSocketHandler paymentSocket;
    @Autowired
    TerminalWebSocketHandler terminalWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(
            WebSocketHandlerRegistry registry
    ) {

        registry.addHandler(terminalWebSocketHandler, "/terminal-socket")
                .setAllowedOrigins("*");


        registry.addHandler(
                paymentSocket,
                "/payment-socket/*"
        ).setAllowedOrigins("*");
    }


}