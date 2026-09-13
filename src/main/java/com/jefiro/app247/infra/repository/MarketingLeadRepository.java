package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.MarketingLead;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketingLeadRepository extends JpaRepository<MarketingLead, String> {
}
