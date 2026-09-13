package com.jefiro.app247.domain.model.dto.admin;

import java.time.Instant;
import java.util.List;

public record AdminDashboardResponse(
        String empresaNome,
        String usuarioNome,
        Instant geradoEm,
        AdminSalesSummaryResponse vendasHoje,
        AdminTerminalSummaryResponse terminais,
        AdminAlertSummaryResponse alertas,
        AdminStockSummaryResponse estoque,
        AdminPaymentAttentionResponse pagamentos,
        AdminOnboardingStatusResponse onboarding,
        List<AdminActivityResponse> atividadeRecente
) {
}
