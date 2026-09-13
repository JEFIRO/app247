package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.TerminalTelemetryMetrics;
import com.jefiro.app247.domain.model.enum_type.TelemetryAlertType;
import com.jefiro.app247.domain.model.enum_type.TerminalOperationalStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class TelemetryHealthService {
    private final TelemetryThresholds thresholds;

    public TelemetryHealthService(TelemetryThresholds thresholds) { this.thresholds = thresholds; }

    public Assessment assess(TerminalTelemetryMetrics m, boolean backendFailurePersistent, Instant now) {
        var severity = TerminalOperationalStatus.SAUDAVEL;
        var reasons = new ArrayList<String>();
        var alerts = new EnumMap<TelemetryAlertType, String>(TelemetryAlertType.class);

        if (Boolean.TRUE.equals(m.getUndervoltageNow())) {
            severity = TerminalOperationalStatus.CRITICO;
            reasons.add("Problema de alimentação detectado");
            alerts.put(TelemetryAlertType.UNDERVOLTAGE, "Subtensão detectada no Raspberry");
        } else if (Boolean.TRUE.equals(m.getUndervoltageOccurred())) {
            severity = worse(severity, TerminalOperationalStatus.ATENCAO);
            reasons.add("Subtensão registrada desde o último boot");
        }
        if (Boolean.TRUE.equals(m.getThrottledNow())) {
            severity = TerminalOperationalStatus.CRITICO;
            reasons.add("Throttling ocorrendo agora");
            alerts.put(TelemetryAlertType.THROTTLING, "Raspberry em throttling");
        } else if (Boolean.TRUE.equals(m.getThrottledOccurred())) {
            severity = worse(severity, TerminalOperationalStatus.ATENCAO);
            reasons.add("Throttling registrado desde o último boot");
        }
        if (atLeast(m.getCpuTemperatureCelsius(), thresholds.getTemperatureCritical())) {
            severity = TerminalOperationalStatus.CRITICO;
            reasons.add("Temperatura crítica");
            alerts.put(TelemetryAlertType.HIGH_TEMPERATURE, "Temperatura crítica da CPU");
        } else if (atLeast(m.getCpuTemperatureCelsius(), thresholds.getTemperatureWarning())) {
            severity = worse(severity, TerminalOperationalStatus.ATENCAO);
            reasons.add("Temperatura elevada");
            alerts.put(TelemetryAlertType.HIGH_TEMPERATURE, "Temperatura elevada da CPU");
        }
        if (atLeast(m.getDiskUsagePercent(), thresholds.getDiskCritical())) {
            severity = TerminalOperationalStatus.CRITICO;
            reasons.add("Armazenamento praticamente cheio");
            alerts.put(TelemetryAlertType.HIGH_DISK_USAGE, "Uso crítico do armazenamento");
        } else if (atLeast(m.getDiskUsagePercent(), thresholds.getDiskWarning())) {
            severity = worse(severity, TerminalOperationalStatus.ATENCAO);
            reasons.add("Armazenamento alto");
            alerts.put(TelemetryAlertType.HIGH_DISK_USAGE, "Uso elevado do armazenamento");
        }
        if (atLeast(m.getCpuUsagePercent(), thresholds.getCpuWarning())) {
            severity = worse(severity, TerminalOperationalStatus.ATENCAO);
            reasons.add("CPU sobrecarregada");
        }
        if (Boolean.FALSE.equals(m.getNetworkConnected())) {
            severity = worse(severity, TerminalOperationalStatus.ATENCAO);
            reasons.add("Wi-Fi desconectado");
            alerts.put(TelemetryAlertType.WEAK_WIFI, "Wi-Fi desconectado");
        } else if (m.getWifiSignalPercent() != null && m.getWifiSignalPercent() < thresholds.getWifiWeak()) {
            severity = worse(severity, TerminalOperationalStatus.ATENCAO);
            reasons.add("Wi-Fi fraco");
            alerts.put(TelemetryAlertType.WEAK_WIFI, "Sinal Wi-Fi fraco");
        }
        if (m.getBackendLatencyMs() != null && m.getBackendLatencyMs() >= thresholds.getLatencyWarningMs()) {
            severity = worse(severity, m.getBackendLatencyMs() >= thresholds.getLatencyCriticalMs()
                    ? TerminalOperationalStatus.CRITICO : TerminalOperationalStatus.ATENCAO);
            reasons.add("Latência elevada");
            alerts.put(TelemetryAlertType.HIGH_BACKEND_LATENCY, "Latência elevada até o backend");
        }
        if (Boolean.FALSE.equals(m.getBackendReachable())) {
            severity = worse(severity, backendFailurePersistent
                    ? TerminalOperationalStatus.CRITICO : TerminalOperationalStatus.ATENCAO);
            reasons.add("Backend inacessível");
        }
        if (!"CONNECTED".equals(m.getWebsocketStatus())) {
            severity = worse(severity, TerminalOperationalStatus.ATENCAO);
            reasons.add("WebSocket desconectado");
            alerts.put(TelemetryAlertType.WEBSOCKET_DISCONNECTED, "WebSocket operacional desconectado");
        }
        boolean syncDelayed = m.getLastSuccessfulSyncAt() != null
                ? Duration.between(m.getLastSuccessfulSyncAt(), now).getSeconds() > thresholds.getSyncDelayedSeconds()
                : m.getAppUptimeSeconds() != null && m.getAppUptimeSeconds() > thresholds.getSyncDelayedSeconds();
        if (syncDelayed) {
            severity = worse(severity, TerminalOperationalStatus.ATENCAO);
            reasons.add("Sincronização atrasada");
            alerts.put(TelemetryAlertType.SYNC_DELAYED, "Sincronização de produtos atrasada");
        }
        return new Assessment(severity, reasons, alerts);
    }

    private boolean atLeast(Double value, double threshold) { return value != null && value >= threshold; }
    private TerminalOperationalStatus worse(TerminalOperationalStatus a, TerminalOperationalStatus b) {
        return rank(a) >= rank(b) ? a : b;
    }
    private int rank(TerminalOperationalStatus status) {
        return switch (status) { case SAUDAVEL -> 0; case ATENCAO -> 1; case CRITICO -> 2; case OFFLINE -> 3; };
    }
    public record Assessment(TerminalOperationalStatus status, List<String> reasons,
                             Map<TelemetryAlertType, String> alerts) {}
}
