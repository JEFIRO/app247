package com.jefiro.app247.infra.event.listener;

import com.jefiro.app247.infra.event.UserCreatedEvent;
import com.jefiro.app247.infra.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class UserListener {
    @Autowired
    EmailService service;

    @Async
    @EventListener
    public void handle(UserCreatedEvent event) throws Exception {
        service.enviarEmail(event.user());
    }
}
