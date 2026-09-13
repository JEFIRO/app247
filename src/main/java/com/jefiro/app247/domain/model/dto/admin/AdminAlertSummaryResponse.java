package com.jefiro.app247.domain.model.dto.admin;

public record AdminAlertSummaryResponse(
        long ativos,
        long info,
        long warning,
        long critical
) {
}
