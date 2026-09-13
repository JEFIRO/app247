package com.jefiro.app247.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Embeddable
@Getter
@Setter
public class TerminalTelemetryMetrics {
    private Long systemUptimeSeconds;
    private Double cpuUsagePercent;
    private Double cpuTemperatureCelsius;
    private Long memoryUsedBytes;
    private Long memoryTotalBytes;
    private Double memoryUsagePercent;
    private Long diskUsedBytes;
    private Long diskTotalBytes;
    private Double diskUsagePercent;
    private Double loadAverage1m;
    private Boolean undervoltageNow;
    private Boolean undervoltageOccurred;
    private Boolean throttledNow;
    private Boolean throttledOccurred;
    private Boolean frequencyCappedNow;
    private Boolean frequencyCappedOccurred;
    private Boolean softTemperatureLimitNow;
    private Boolean softTemperatureLimitOccurred;
    @Column(length = 30) private String throttledRaw;

    private Boolean networkConnected;
    @Column(length = 80) private String interfaceName;
    @Column(length = 120) private String wifiSsid;
    @Column(length = 45) private String localIp;
    private Integer wifiSignalPercent;
    @Column(length = 20) private String wifiSignalQuality;
    private Boolean backendReachable;
    private Long backendLatencyMs;

    @Column(length = 60) private String appVersion;
    private Long appUptimeSeconds;
    @Column(length = 20) private String websocketStatus;
    private Instant lastProductSyncStartedAt;
    private Instant lastProductSyncCompletedAt;
    private Instant lastSuccessfulSyncAt;
    @Column(length = 300) private String lastSyncError;
    private Boolean purchaseActive;
    private Boolean paymentInProgress;

    private Integer displayWidth;
    private Integer displayHeight;
    @Column(length = 20) private String displayOrientation;
}
