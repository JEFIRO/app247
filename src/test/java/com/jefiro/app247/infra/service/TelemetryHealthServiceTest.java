package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.TerminalTelemetryMetrics;
import com.jefiro.app247.domain.model.enum_type.TelemetryAlertType;
import com.jefiro.app247.domain.model.enum_type.TerminalOperationalStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TelemetryHealthServiceTest {
    private TelemetryHealthService service;

    @BeforeEach
    void setUp() {
        service = new TelemetryHealthService(new TelemetryThresholds(
                70, 80, 80, 90, 90, 35, 500, 1500, 900, 60));
    }

    @Test
    void subtensaoAtualEhCriticaEAbreAlerta() {
        var metrics = healthy();
        metrics.setUndervoltageNow(true);

        var result = service.assess(metrics, false, Instant.now());

        assertThat(result.status()).isEqualTo(TerminalOperationalStatus.CRITICO);
        assertThat(result.alerts()).containsKey(TelemetryAlertType.UNDERVOLTAGE);
        assertThat(result.reasons()).contains("Problema de alimentação detectado");
    }

    @Test
    void historicoDeSubtensaoEhAtencaoSemAlertaAtivo() {
        var metrics = healthy();
        metrics.setUndervoltageOccurred(true);

        var result = service.assess(metrics, false, Instant.now());

        assertThat(result.status()).isEqualTo(TerminalOperationalStatus.ATENCAO);
        assertThat(result.alerts()).doesNotContainKey(TelemetryAlertType.UNDERVOLTAGE);
    }

    @Test
    void backendSoFicaCriticoDepoisDeFalhaPersistente() {
        var metrics = healthy();
        metrics.setBackendReachable(false);
        assertThat(service.assess(metrics, false, Instant.now()).status())
                .isEqualTo(TerminalOperationalStatus.ATENCAO);
        assertThat(service.assess(metrics, true, Instant.now()).status())
                .isEqualTo(TerminalOperationalStatus.CRITICO);
    }

    @Test
    void thresholdsDeTemperaturaDiscoWifiLatenciaESyncSaoCentralizados() {
        var metrics = healthy();
        metrics.setCpuTemperatureCelsius(70d);
        metrics.setDiskUsagePercent(85d);
        metrics.setWifiSignalPercent(20);
        metrics.setBackendLatencyMs(700L);
        metrics.setLastSuccessfulSyncAt(Instant.now().minusSeconds(901));

        var result = service.assess(metrics, false, Instant.now());

        assertThat(result.status()).isEqualTo(TerminalOperationalStatus.ATENCAO);
        assertThat(result.alerts().keySet()).contains(
                TelemetryAlertType.HIGH_TEMPERATURE,
                TelemetryAlertType.HIGH_DISK_USAGE,
                TelemetryAlertType.WEAK_WIFI,
                TelemetryAlertType.HIGH_BACKEND_LATENCY,
                TelemetryAlertType.SYNC_DELAYED);
    }

    @Test
    void wifiConfirmadamenteDesconectadoGeraAtencaoSemInventarMedicaoAusente() {
        var unavailable = healthy();
        unavailable.setNetworkConnected(null);
        assertThat(service.assess(unavailable, false, Instant.now()).status())
                .isEqualTo(TerminalOperationalStatus.SAUDAVEL);

        var disconnected = healthy();
        disconnected.setNetworkConnected(false);
        var result = service.assess(disconnected, false, Instant.now());
        assertThat(result.status()).isEqualTo(TerminalOperationalStatus.ATENCAO);
        assertThat(result.alerts()).containsKey(TelemetryAlertType.WEAK_WIFI);
    }

    private TerminalTelemetryMetrics healthy() {
        var metrics = new TerminalTelemetryMetrics();
        metrics.setUndervoltageNow(false);
        metrics.setUndervoltageOccurred(false);
        metrics.setThrottledNow(false);
        metrics.setThrottledOccurred(false);
        metrics.setBackendReachable(true);
        metrics.setWebsocketStatus("CONNECTED");
        metrics.setAppUptimeSeconds(10L);
        metrics.setLastSuccessfulSyncAt(Instant.now());
        return metrics;
    }
}
