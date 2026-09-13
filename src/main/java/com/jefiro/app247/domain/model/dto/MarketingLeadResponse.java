package com.jefiro.app247.domain.model.dto;

import com.jefiro.app247.domain.model.MarketingLead;
import com.jefiro.app247.domain.model.enum_type.LeadStatus;

import java.time.Instant;

public record MarketingLeadResponse(String id, LeadStatus status, Instant createdAt) {
    public static MarketingLeadResponse from(MarketingLead lead) {
        return new MarketingLeadResponse(lead.getId(), lead.getStatus(), lead.getCreatedAt());
    }
}
