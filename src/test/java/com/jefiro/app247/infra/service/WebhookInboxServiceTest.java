package com.jefiro.app247.infra.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.PaymentAttempt;
import com.jefiro.app247.domain.model.WebhookEvent;
import com.jefiro.app247.domain.model.enum_type.PaymentProvider;
import com.jefiro.app247.infra.repository.PagamentoRepository;
import com.jefiro.app247.infra.repository.WebhookEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WebhookInboxServiceTest {
    WebhookEventRepository repository;
    PagamentoRepository pagamentoRepository;
    WebhookInboxService service;

    @BeforeEach
    void setUp() {
        repository = mock(WebhookEventRepository.class);
        pagamentoRepository = mock(PagamentoRepository.class);
        service = new WebhookInboxService(repository, pagamentoRepository, new ObjectMapper());
    }

    @Test
    void registraEventoDeterministicoESanitizaSegredo() {
        when(repository.findByProviderAndEventId(any(), any())).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        var result = service.register("order.created", "ORD1", 3,
                "{\"action\":\"order.created\",\"access_token\":\"secret\",\"data\":{\"id\":\"ORD1\",\"version\":3}}");

        assertThat(result.created()).isTrue();
        assertThat(result.event().getEventId()).hasSize(64);
        assertThat(result.event().getPayload()).contains("[REDACTED]").doesNotContain("secret");
        assertThat(result.event().getProcessingStatus()).isEqualTo("RECEIVED");
    }

    @Test
    void processamentoVinculaTentativaETenant() {
        String payload = "{\"action\":\"order.processed\",\"data\":{\"id\":\"ORD1\",\"version\":4,\"external_reference\":\"attempt-1\"}}";
        String eventId = WebhookInboxService.eventId("order.processed", "ORD1", 4);
        WebhookEvent event = new WebhookEvent();
        event.setEventId(eventId);
        PaymentAttempt attempt = new PaymentAttempt();
        Empresa empresa = Empresa.builder().id("empresa-1").build();
        attempt.setEmpresa(empresa);
        when(repository.findByProviderAndEventId(PaymentProvider.MERCADO_PAGO, eventId))
                .thenReturn(Optional.of(event));
        when(pagamentoRepository.findByProviderAndExternalReference(
                PaymentProvider.MERCADO_PAGO, "attempt-1")).thenReturn(Optional.of(attempt));

        service.markProcessedSafely(payload);

        assertThat(event.getPaymentAttempt()).isSameAs(attempt);
        assertThat(event.getEmpresa()).isSameAs(empresa);
        assertThat(event.getProcessingStatus()).isEqualTo("PROCESSED");
        assertThat(event.getProcessedAt()).isNotNull();
        verify(repository).save(event);
    }
}
