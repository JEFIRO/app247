package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.MarketingLead;
import com.jefiro.app247.domain.model.dto.MarketingLeadRequest;
import com.jefiro.app247.domain.model.dto.MarketingLeadResponse;
import com.jefiro.app247.infra.service.MarketingLeadRateLimiter;
import com.jefiro.app247.infra.service.MarketingLeadService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/public/leads")
public class MarketingLeadController {
    @Autowired
    private MarketingLeadService service;
    @Autowired
    private MarketingLeadRateLimiter rateLimiter;

    @PostMapping
    public ResponseEntity<MarketingLeadResponse> criar(
            @RequestBody @Valid MarketingLeadRequest request,
            HttpServletRequest servletRequest) {
        String ip = clientIp(servletRequest);
        if (!rateLimiter.allow(ip, request.email())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Aguarde antes de enviar novamente.");
        }
        if (request.website() != null && !request.website().isBlank()) {
            return ResponseEntity.accepted().build();
        }
        MarketingLead lead = service.criar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(MarketingLeadResponse.from(lead));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
