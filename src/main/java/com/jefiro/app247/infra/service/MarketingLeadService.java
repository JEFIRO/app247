package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.MarketingLead;
import com.jefiro.app247.domain.model.dto.MarketingLeadRequest;
import com.jefiro.app247.domain.model.enum_type.LeadStatus;
import com.jefiro.app247.infra.repository.MarketingLeadRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MarketingLeadService {
    @Autowired
    private MarketingLeadRepository repository;

    @Transactional
    public MarketingLead criar(MarketingLeadRequest request) {
        MarketingLead lead = new MarketingLead();
        lead.setNome(clean(request.nome()));
        lead.setEmpresa(clean(request.empresa()));
        lead.setEmail(clean(request.email()).toLowerCase());
        lead.setTelefone(clean(request.telefone()));
        lead.setCidade(clean(request.cidade()));
        lead.setEstado(clean(request.estado()));
        lead.setQuantidadeUnidades(request.quantidadeUnidades());
        lead.setQuantidadeTerminais(request.quantidadeTerminais());
        lead.setJaOperaMercadoAutonomo(clean(request.jaOperaMercadoAutonomo()));
        lead.setMensagem(cleanMessage(request.mensagem()));
        lead.setUtmSource(clean(request.utmSource()));
        lead.setUtmMedium(clean(request.utmMedium()));
        lead.setUtmCampaign(clean(request.utmCampaign()));
        lead.setUtmContent(clean(request.utmContent()));
        lead.setUtmTerm(clean(request.utmTerm()));
        lead.setLandingPage(clean(request.landingPage()));
        lead.setStatus(LeadStatus.NEW);
        return repository.save(lead);
    }

    private String clean(String value) {
        if (value == null) return null;
        String cleaned = value.trim().replaceAll("\\s+", " ");
        return cleaned.isEmpty() ? null : cleaned;
    }

    private String cleanMessage(String value) {
        if (value == null) return null;
        return value.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "").trim();
    }
}
