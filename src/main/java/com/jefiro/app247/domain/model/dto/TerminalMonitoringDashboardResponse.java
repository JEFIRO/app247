package com.jefiro.app247.domain.model.dto;

import java.util.List;

public record TerminalMonitoringDashboardResponse(
        long total, long online, long saudaveis, long atencao, long criticos, long offline,
        List<TerminalTelemetryResponse> terminals) {}
