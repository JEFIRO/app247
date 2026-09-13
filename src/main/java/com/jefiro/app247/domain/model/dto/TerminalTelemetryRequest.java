package com.jefiro.app247.domain.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;

public record TerminalTelemetryRequest(
        @NotBlank String terminalUuid,
        @NotNull Instant capturedAt,
        @NotNull @Valid SystemMetrics system,
        @NotNull @Valid NetworkMetrics network,
        @NotNull @Valid ApplicationMetrics application,
        @NotNull @Valid DisplayMetrics display
) {
    public record SystemMetrics(
            @PositiveOrZero Long uptimeSeconds,
            @DecimalMin("0") @DecimalMax("100") Double cpuUsagePercent,
            Double cpuTemperatureCelsius,
            @PositiveOrZero Long memoryUsedBytes, @Positive Long memoryTotalBytes,
            @DecimalMin("0") @DecimalMax("100") Double memoryUsagePercent,
            @PositiveOrZero Long diskUsedBytes, @Positive Long diskTotalBytes,
            @DecimalMin("0") @DecimalMax("100") Double diskUsagePercent,
            @PositiveOrZero Double loadAverage1m,
            Boolean undervoltageNow, Boolean undervoltageOccurred,
            Boolean throttledNow, Boolean throttledOccurred,
            Boolean frequencyCappedNow, Boolean frequencyCappedOccurred,
            Boolean softTemperatureLimitNow, Boolean softTemperatureLimitOccurred,
            @Size(max = 30) String throttledRaw) {}

    public record NetworkMetrics(
            Boolean connected, @Size(max = 80) String interfaceName,
            @Size(max = 120) String ssid, @Size(max = 45) String localIp,
            @Min(0) @Max(100) Integer wifiSignalPercent,
            @Size(max = 20) String wifiSignalQuality,
            Boolean backendReachable, @PositiveOrZero Long backendLatencyMs) {}

    public record ApplicationMetrics(
            @NotBlank @Size(max = 60) String version,
            @PositiveOrZero Long uptimeSeconds,
            @Pattern(regexp = "CONNECTED|DISCONNECTED|RECONNECTING") String websocketStatus,
            Instant lastProductSyncStartedAt, Instant lastProductSyncCompletedAt,
            Instant lastSuccessfulSyncAt, @Size(max = 300) String lastSyncError,
            Boolean purchaseActive, Boolean paymentInProgress) {}

    public record DisplayMetrics(
            @Positive Integer width, @Positive Integer height,
            @Pattern(regexp = "HORIZONTAL|VERTICAL") String orientation) {}
}
