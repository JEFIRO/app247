package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.MarketingLead;
import com.jefiro.app247.domain.model.dto.MarketingLeadRequest;
import com.jefiro.app247.domain.model.enum_type.LeadStatus;
import com.jefiro.app247.infra.repository.MarketingLeadRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarketingLeadServiceTest {
    @Test
    void criaLeadNovoComCamposSanitizadosSemBloquearDuplicidade() {
        MarketingLeadRepository repository = mock(MarketingLeadRepository.class);
        when(repository.save(any(MarketingLead.class))).thenAnswer(invocation -> invocation.getArgument(0));
        MarketingLeadService service = new MarketingLeadService();
        ReflectionTestUtils.setField(service, "repository", repository);

        MarketingLeadRequest request = new MarketingLeadRequest(
                "  Maria   Silva ", " Mercado Teste ", " MARIA@EXAMPLE.COM ",
                "(71) 99999-9999", "Salvador", "BA", 2, 3, "SIM",
                " Gostaria de conhecer a solução. ", "google", "cpc", "campanha",
                null, null, "https://app247.com.br/contato", null);

        MarketingLead lead = service.criar(request);

        assertThat(lead.getNome()).isEqualTo("Maria Silva");
        assertThat(lead.getEmail()).isEqualTo("maria@example.com");
        assertThat(lead.getStatus()).isEqualTo(LeadStatus.NEW);
    }
}
