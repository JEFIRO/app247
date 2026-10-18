package com.jefiro.app247.infra.controller;

import com.jefiro.app247.infra.dto.onboarding.CadastroCompletoRequest;
import com.jefiro.app247.infra.dto.onboarding.OnboardingResponse;
import com.jefiro.app247.infra.service.OnboardingService;
import com.jefiro.app247.infra.service.PublicAuthRateLimits;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OnboardingController {
    private final OnboardingService service;
    private final PublicAuthRateLimits limits;

    public OnboardingController(OnboardingService service, PublicAuthRateLimits limits) {
        this.service = service;
        this.limits = limits;
    }

    @PostMapping({"/onboarding", "/condominio"})
    public ResponseEntity<OnboardingResponse> criar(@RequestBody @Valid CadastroCompletoRequest request,
                                                    HttpServletRequest httpRequest) {
        limits.beforeOnboarding(httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(service.executar(request));
    }
}
