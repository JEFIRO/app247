package com.jefiro.app247.domain.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MarketingLeadRequest(
        @NotBlank @Size(min = 2, max = 100) String nome,
        @Size(max = 120) String empresa,
        @NotBlank @Email @Size(max = 160) String email,
        @NotBlank @Size(max = 30)
        @Pattern(regexp = "^[+()\\d\\s-]{10,30}$", message = "telefone inválido") String telefone,
        @Size(max = 100) String cidade,
        @Size(max = 40) String estado,
        @Min(0) @Max(10000) Integer quantidadeUnidades,
        @Min(0) @Max(10000) Integer quantidadeTerminais,
        @Size(max = 30) String jaOperaMercadoAutonomo,
        @NotBlank @Size(min = 10, max = 2000) String mensagem,
        @Size(max = 160) String utmSource,
        @Size(max = 160) String utmMedium,
        @Size(max = 160) String utmCampaign,
        @Size(max = 160) String utmContent,
        @Size(max = 160) String utmTerm,
        @Size(max = 500) String landingPage,
        @Size(max = 200) String website
) {}
