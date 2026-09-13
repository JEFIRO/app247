package com.jefiro.app247.domain.model.dto;

import com.jefiro.app247.domain.model.TerminalTelemetryAlert;
import com.jefiro.app247.domain.model.TerminalTelemetryCurrent;
import com.jefiro.app247.domain.model.TerminalTelemetryMetrics;
import com.jefiro.app247.domain.model.enum_type.TerminalOperationalStatus;

import java.time.Instant;
import java.util.List;
import com.jefiro.app247.domain.model.terminal.Terminal;

public record TerminalTelemetryResponse(
        String terminalId, String terminalNome, String condominioId, String condominioNome,
        boolean online, TerminalOperationalStatus status, List<String> reasons,
        Instant lastHeartbeatAt, Instant capturedAt, Instant receivedAt,
        TerminalTelemetryRequest.SystemMetrics system,
        TerminalTelemetryRequest.NetworkMetrics network,
        TerminalTelemetryRequest.ApplicationMetrics application,
        TerminalTelemetryRequest.DisplayMetrics display,
        List<AlertResponse> activeAlerts
) {
    public static TerminalTelemetryResponse from(TerminalTelemetryCurrent current,
                                                  boolean online,
                                                  TerminalOperationalStatus effectiveStatus,
                                                  List<TerminalTelemetryAlert> alerts) {
        var terminal = current.getTerminal();
        var metrics = current.getMetrics();
        return new TerminalTelemetryResponse(
                terminal.getIdTerminal(), terminal.getNome(),
                terminal.getCondominio().getIdCondominio(), terminal.getCondominio().getNome(),
                online, effectiveStatus, splitReasons(current.getStatusReasons()),
                terminal.getLastPing(),
                current.getCapturedAt(), current.getReceivedAt(),
                system(metrics), network(metrics), application(metrics), display(metrics),
                alerts.stream().map(AlertResponse::from).toList());
    }

    public static TerminalTelemetryResponse withoutTelemetry(Terminal terminal, boolean online,
                                                              TerminalOperationalStatus status,
                                                              List<TerminalTelemetryAlert> alerts) {
        return new TerminalTelemetryResponse(
                terminal.getIdTerminal(), terminal.getNome(), terminal.getCondominio().getIdCondominio(),
                terminal.getCondominio().getNome(), online, status,
                List.of("Telemetria ainda não recebida"),
                terminal.getLastPing(),
                null, null, null, null, null, null,
                alerts.stream().map(AlertResponse::from).toList());
    }

    private static List<String> splitReasons(String value) {
        return value == null || value.isBlank() ? List.of() : List.of(value.split("\\|"));
    }

    public static TerminalTelemetryRequest.SystemMetrics system(TerminalTelemetryMetrics m) {
        return new TerminalTelemetryRequest.SystemMetrics(
                m.getSystemUptimeSeconds(), m.getCpuUsagePercent(), m.getCpuTemperatureCelsius(),
                m.getMemoryUsedBytes(), m.getMemoryTotalBytes(), m.getMemoryUsagePercent(),
                m.getDiskUsedBytes(), m.getDiskTotalBytes(), m.getDiskUsagePercent(),
                m.getLoadAverage1m(), m.getUndervoltageNow(), m.getUndervoltageOccurred(),
                m.getThrottledNow(), m.getThrottledOccurred(), m.getFrequencyCappedNow(),
                m.getFrequencyCappedOccurred(), m.getSoftTemperatureLimitNow(),
                m.getSoftTemperatureLimitOccurred(), m.getThrottledRaw());
    }

    public static TerminalTelemetryRequest.NetworkMetrics network(TerminalTelemetryMetrics m) {
        return new TerminalTelemetryRequest.NetworkMetrics(
                m.getNetworkConnected(), m.getInterfaceName(), m.getWifiSsid(), m.getLocalIp(),
                m.getWifiSignalPercent(), m.getWifiSignalQuality(),
                m.getBackendReachable(), m.getBackendLatencyMs());
    }

    public static TerminalTelemetryRequest.ApplicationMetrics application(TerminalTelemetryMetrics m) {
        return new TerminalTelemetryRequest.ApplicationMetrics(
                m.getAppVersion(), m.getAppUptimeSeconds(), m.getWebsocketStatus(),
                m.getLastProductSyncStartedAt(), m.getLastProductSyncCompletedAt(),
                m.getLastSuccessfulSyncAt(), m.getLastSyncError(),
                m.getPurchaseActive(), m.getPaymentInProgress());
    }

    public static TerminalTelemetryRequest.DisplayMetrics display(TerminalTelemetryMetrics m) {
        return new TerminalTelemetryRequest.DisplayMetrics(
                m.getDisplayWidth(), m.getDisplayHeight(), m.getDisplayOrientation());
    }

    public record AlertResponse(String id, String type, String status, String message,
                                Instant openedAt, Instant lastObservedAt, Instant resolvedAt) {
        public static AlertResponse from(TerminalTelemetryAlert alert) {
            return new AlertResponse(alert.getId(), alert.getType().name(), alert.getStatus().name(),
                    alert.getMessage(), alert.getOpenedAt(), alert.getLastObservedAt(), alert.getResolvedAt());
        }
    }
}
