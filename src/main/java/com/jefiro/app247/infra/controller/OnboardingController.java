package com.jefiro.app247.infra.controller;

import com.jefiro.app247.infra.dto.onboarding.CadastroCompletoRequest;
import com.jefiro.app247.infra.dto.onboarding.OnboardingResponse;
import com.jefiro.app247.infra.service.OnboardingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OnboardingController {
    private final OnboardingService service;

    public OnboardingController(OnboardingService service) {
        this.service = service;
    }

    @PostMapping({"/onboarding", "/condominio"})
    public ResponseEntity<OnboardingResponse> criar(@RequestBody @Valid CadastroCompletoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.executar(request));
    }
}
