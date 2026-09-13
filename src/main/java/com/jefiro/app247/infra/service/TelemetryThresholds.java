package com.jefiro.app247.infra.service;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
public class TelemetryThresholds {
    private final double temperatureWarning;
    private final double temperatureCritical;
    private final double diskWarning;
    private final double diskCritical;
    private final double cpuWarning;
    private final int wifiWeak;
    private final long latencyWarningMs;
    private final long latencyCriticalMs;
    private final long syncDelayedSeconds;
    private final long offlineSeconds;

    public TelemetryThresholds(
            @Value("${terminal.telemetry.thresholds.temperature-warning:70}") double temperatureWarning,
            @Value("${terminal.telemetry.thresholds.temperature-critical:80}") double temperatureCritical,
            @Value("${terminal.telemetry.thresholds.disk-warning:80}") double diskWarning,
            @Value("${terminal.telemetry.thresholds.disk-critical:90}") double diskCritical,
            @Value("${terminal.telemetry.thresholds.cpu-warning:90}") double cpuWarning,
            @Value("${terminal.telemetry.thresholds.wifi-weak:35}") int wifiWeak,
            @Value("${terminal.telemetry.thresholds.latency-warning-ms:500}") long latencyWarningMs,
            @Value("${terminal.telemetry.thresholds.latency-critical-ms:1500}") long latencyCriticalMs,
            @Value("${terminal.telemetry.thresholds.sync-delayed-seconds:900}") long syncDelayedSeconds,
            @Value("${terminal.telemetry.offline-seconds:60}") long offlineSeconds) {
        this.temperatureWarning = temperatureWarning;
        this.temperatureCritical = temperatureCritical;
        this.diskWarning = diskWarning;
        this.diskCritical = diskCritical;
        this.cpuWarning = cpuWarning;
        this.wifiWeak = wifiWeak;
        this.latencyWarningMs = latencyWarningMs;
        this.latencyCriticalMs = latencyCriticalMs;
        this.syncDelayedSeconds = syncDelayedSeconds;
        this.offlineSeconds = offlineSeconds;
    }
}
