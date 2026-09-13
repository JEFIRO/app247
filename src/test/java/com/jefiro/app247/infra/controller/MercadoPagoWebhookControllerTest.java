package com.jefiro.app247.infra.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jefiro.app247.infra.service.MercadoPagoWebhookSignatureService;
import com.jefiro.app247.infra.service.WebhookInboxService;
import com.jefiro.app247.domain.model.WebhookEvent;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MercadoPagoWebhookControllerTest {

    @Test
    void webhookDuplicadoEhConfirmadoSemEnfileirarNovamente() throws Exception {
        RedisTemplate<String, String> redis = mock(RedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        ListOperations<String, String> lists = mock(ListOperations.class);
        MercadoPagoWebhookSignatureService signatures = mock(MercadoPagoWebhookSignatureService.class);
        WebhookInboxService inbox = mock(WebhookInboxService.class);
        when(redis.opsForValue()).thenReturn(values);
        when(redis.opsForList()).thenReturn(lists);
        when(signatures.isConfigured()).thenReturn(true);
        when(signatures.isValid(any(), any(), any())).thenReturn(true);
        when(values.setIfAbsent(anyString(), eq("received"), any(Duration.class)))
                .thenReturn(true, false);
        WebhookEvent event = new WebhookEvent();
        event.setEventId("event-1");
        event.setProcessingStatus("RECEIVED");
        when(inbox.register(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(new WebhookInboxService.Registration(event, true),
                        new WebhookInboxService.Registration(event, false));

        MercadoPagoWebhookController controller = new MercadoPagoWebhookController(
                redis, new ObjectMapper(), signatures, inbox
        );
        Map<String, Object> body = Map.of(
                "action", "order.processed",
                "data", Map.of("id", "ORD1", "version", 3)
        );

        assertThat(controller.receive("signature", "request", "ORD1", body).getBody())
                .isEqualTo("ENFILEIRADO");
        assertThat(controller.receive("signature", "request", "ORD1", body).getBody())
                .isEqualTo("DUPLICADO");
        verify(lists, times(1)).leftPush(eq("mp_queue"), anyString());
        verify(inbox).markQueued("event-1");
    }
}
