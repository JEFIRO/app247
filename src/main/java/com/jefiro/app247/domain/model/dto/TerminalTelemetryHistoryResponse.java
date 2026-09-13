package com.jefiro.app247.domain.model.dto;

import com.jefiro.app247.domain.model.TerminalTelemetryHistory;
import com.jefiro.app247.domain.model.enum_type.TerminalOperationalStatus;

import java.time.Instant;

public record TerminalTelemetryHistoryResponse(
        Instant capturedAt, TerminalOperationalStatus status,
        Double cpuUsagePercent, Double cpuTemperatureCelsius,
        Double memoryUsagePercent, Double diskUsagePercent,
        Long backendLatencyMs, Integer wifiSignalPercent) {
    public static TerminalTelemetryHistoryResponse from(TerminalTelemetryHistory item) {
        var m = item.getMetrics();
        return new TerminalTelemetryHistoryResponse(item.getCapturedAt(), item.getOperationalStatus(),
                m.getCpuUsagePercent(), m.getCpuTemperatureCelsius(), m.getMemoryUsagePercent(),
                m.getDiskUsagePercent(), m.getBackendLatencyMs(), m.getWifiSignalPercent());
    }
}
