package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.MarketingLead;
import com.jefiro.app247.domain.model.dto.MarketingLeadRequest;
import com.jefiro.app247.domain.model.enum_type.LeadStatus;
import com.jefiro.app247.infra.service.MarketingLeadRateLimiter;
import com.jefiro.app247.infra.service.MarketingLeadService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MarketingLeadControllerTest {
    @Test
    void persisteLeadValidoERetornaCreated() {
        MarketingLeadService service = mock(MarketingLeadService.class);
        MarketingLeadRateLimiter limiter = mock(MarketingLeadRateLimiter.class);
        MarketingLeadController controller = controller(service, limiter);
        MarketingLeadRequest request = request(null);
        MarketingLead saved = new MarketingLead();
        saved.setId("lead-id");
        saved.setStatus(LeadStatus.NEW);
        saved.setCreatedAt(Instant.parse("2026-09-06T12:00:00Z"));
        when(limiter.allow("203.0.113.10", request.email())).thenReturn(true);
        when(service.criar(request)).thenReturn(saved);
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.addHeader("X-Forwarded-For", "203.0.113.10");

        var response = controller.criar(request, servletRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isEqualTo("lead-id");
    }

    @Test
    void honeypotNaoEhPersistido() {
        MarketingLeadService service = mock(MarketingLeadService.class);
        MarketingLeadRateLimiter limiter = mock(MarketingLeadRateLimiter.class);
        MarketingLeadController controller = controller(service, limiter);
        MarketingLeadRequest request = request("spam.example");
        when(limiter.allow("127.0.0.1", request.email())).thenReturn(true);

        var response = controller.criar(request, new MockHttpServletRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verifyNoInteractions(service);
    }

    private MarketingLeadController controller(MarketingLeadService service, MarketingLeadRateLimiter limiter) {
        MarketingLeadController controller = new MarketingLeadController();
        ReflectionTestUtils.setField(controller, "service", service);
        ReflectionTestUtils.setField(controller, "rateLimiter", limiter);
        return controller;
    }

    private MarketingLeadRequest request(String website) {
        return new MarketingLeadRequest(
                "Maria Silva", "Mercado Teste", "maria@example.com", "(71) 99999-9999",
                "Salvador", "BA", 2, 3, "SIM", "Gostaria de uma demonstração.",
                null, null, null, null, null, "https://app247.com.br/contato", website);
    }
}
