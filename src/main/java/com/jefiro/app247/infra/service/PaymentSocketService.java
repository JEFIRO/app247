package com.jefiro.app247.infra.service;

import com.jefiro.app247.infra.event.PaymentEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@RequiredArgsConstructor
public class PaymentSocketService {

    private final SimpMessagingTemplate messagingTemplate;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void sendPaymentEvent(PaymentEvent event) {
        messagingTemplate.convertAndSend("/topic/payment/" + event.getTerminalId(), event);
    }
}
