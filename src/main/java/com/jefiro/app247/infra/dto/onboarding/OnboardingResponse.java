package com.jefiro.app247.infra.dto.onboarding;

public record OnboardingResponse(
        String gestorId,
        String empresaId,
        String condominioId,
        String terminalId
) {
}
