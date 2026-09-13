package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.*;
import com.jefiro.app247.domain.model.dto.*;
import com.jefiro.app247.domain.model.enum_type.TelemetryAlertStatus;
import com.jefiro.app247.domain.model.enum_type.TerminalOperationalStatus;
import com.jefiro.app247.domain.model.terminal.Terminal;
import com.jefiro.app247.infra.exception.TerminalNotFoundException;
import com.jefiro.app247.infra.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TerminalTelemetryService {
    private final TerminalRepository terminalRepository;
    private final TerminalTelemetryCurrentRepository currentRepository;
    private final TerminalTelemetryHistoryRepository historyRepository;
    private final TerminalTelemetryAlertRepository alertRepository;
    private final TelemetryHealthService healthService;
    private final TelemetryAlertService alertService;
    private final TerminalPresenceService presenceService;

    public TerminalTelemetryService(TerminalRepository terminalRepository,
                                    TerminalTelemetryCurrentRepository currentRepository,
                                    TerminalTelemetryHistoryRepository historyRepository,
                                    TerminalTelemetryAlertRepository alertRepository,
                                    TelemetryHealthService healthService,
                                    TelemetryAlertService alertService,
                                    TerminalPresenceService presenceService) {
        this.terminalRepository = terminalRepository;
        this.currentRepository = currentRepository;
        this.historyRepository = historyRepository;
        this.alertRepository = alertRepository;
        this.healthService = healthService;
        this.alertService = alertService;
        this.presenceService = presenceService;
    }

    @Transactional
    public TerminalTelemetryResponse receive(TerminalTelemetryRequest request) {
        Terminal terminal = terminalRepository.findByIdForTelemetryUpdate(request.terminalUuid())
                .orElseThrow(TerminalNotFoundException::new);
        if (!Boolean.TRUE.equals(terminal.getAtivo())) {
            throw new IllegalArgumentException("Terminal está inativo");
        }
        Instant receivedAt = Instant.now();
        if (request.capturedAt().isAfter(receivedAt.plus(Duration.ofMinutes(5)))) {
            throw new IllegalArgumentException("capturedAt está no futuro");
        }
        TerminalTelemetryMetrics metrics = map(request);
        var previous = currentRepository.findById(terminal.getIdTerminal());
        boolean persistentBackendFailure = previous
                .map(item -> Boolean.FALSE.equals(item.getMetrics().getBackendReachable()))
                .orElse(false) && Boolean.FALSE.equals(metrics.getBackendReachable());
        var assessment = healthService.assess(metrics, persistentBackendFailure, receivedAt);

        TerminalTelemetryHistory history = new TerminalTelemetryHistory();
        history.setTerminal(terminal);
        history.setCapturedAt(request.capturedAt());
        history.setReceivedAt(receivedAt);
        history.setOperationalStatus(assessment.status());
        history.setMetrics(metrics);
        historyRepository.save(history);

        TerminalTelemetryCurrent current = previous.orElseGet(TerminalTelemetryCurrent::new);
        if (current.getCapturedAt() == null || request.capturedAt().isAfter(current.getCapturedAt())) {
            current.setTerminal(terminal);
            current.setCapturedAt(request.capturedAt());
            current.setReceivedAt(receivedAt);
            current.setOperationalStatus(assessment.status());
            current.setStatusReasons(String.join("|", assessment.reasons()));
            current.setMetrics(copy(metrics));
            currentRepository.save(current);
            terminal.setVersaoSoftware(metrics.getAppVersion());
            if (metrics.getLocalIp() != null && !metrics.getLocalIp().isBlank()) {
                terminal.setIpAddress(metrics.getLocalIp());
            }
            terminalRepository.save(terminal);
            alertService.reconcile(terminal, assessment.alerts(), receivedAt);
        }
        return detailFor(current, presenceService.isOnline(terminal));
    }

    @Transactional(readOnly = true)
    public TerminalMonitoringDashboardResponse dashboard(String condominioId,
                                                          TerminalOperationalStatus status) {
        String empresaId = EmpresaContext.require();
        List<Terminal> terminals = terminalRepository.findAllByCondominioEmpresaIdOrderByNome(empresaId);
        if (condominioId != null && !condominioId.isBlank()) {
            terminals = terminals.stream().filter(t -> condominioId.equals(
                    t.getCondominio().getIdCondominio())).toList();
        }
        Map<String, TerminalTelemetryCurrent> current = currentRepository
                .findAllByTerminal_Condominio_Empresa_Id(empresaId).stream()
                .collect(Collectors.toMap(TerminalTelemetryCurrent::getTerminalId, Function.identity()));
        List<TerminalTelemetryResponse> allItems = terminals.stream().map(terminal -> {
            boolean online = presenceService.isOnline(terminal);
            var telemetry = current.get(terminal.getIdTerminal());
            var alerts = alertRepository.findByTerminalIdTerminalAndStatusOrderByOpenedAtDesc(
                    terminal.getIdTerminal(), TelemetryAlertStatus.ACTIVE);
            if (telemetry == null) return TerminalTelemetryResponse.withoutTelemetry(
                    terminal, online, online ? TerminalOperationalStatus.ATENCAO
                            : TerminalOperationalStatus.OFFLINE, alerts);
            return detailFor(telemetry, online);
        }).toList();
        List<TerminalTelemetryResponse> visibleItems = allItems.stream()
                .filter(item -> status == null || item.status() == status).toList();
        long online = allItems.stream().filter(TerminalTelemetryResponse::online).count();
        return new TerminalMonitoringDashboardResponse(
                allItems.size(), online,
                count(allItems, TerminalOperationalStatus.SAUDAVEL),
                count(allItems, TerminalOperationalStatus.ATENCAO),
                count(allItems, TerminalOperationalStatus.CRITICO),
                count(allItems, TerminalOperationalStatus.OFFLINE), visibleItems);
    }

    @Transactional(readOnly = true)
    public TerminalTelemetryResponse detail(String terminalId) {
        Terminal terminal = tenantTerminal(terminalId);
        boolean online = presenceService.isOnline(terminal);
        var alerts = alertRepository.findByTerminalIdTerminalAndStatusOrderByOpenedAtDesc(
                terminalId, TelemetryAlertStatus.ACTIVE);
        return currentRepository.findByTerminal_IdTerminalAndTerminal_Condominio_Empresa_Id(
                        terminalId, EmpresaContext.require())
                .map(current -> TerminalTelemetryResponse.from(current, online,
                        online ? current.getOperationalStatus() : TerminalOperationalStatus.OFFLINE, alerts))
                .orElseGet(() -> TerminalTelemetryResponse.withoutTelemetry(terminal, online,
                        online ? TerminalOperationalStatus.ATENCAO : TerminalOperationalStatus.OFFLINE, alerts));
    }

    @Transactional(readOnly = true)
    public List<TerminalTelemetryHistoryResponse> history(String terminalId, String period) {
        tenantTerminal(terminalId);
        Duration duration = switch (period == null ? "1h" : period) {
            case "1h" -> Duration.ofHours(1); case "6h" -> Duration.ofHours(6);
            case "24h" -> Duration.ofHours(24); case "7d" -> Duration.ofDays(7);
            default -> throw new IllegalArgumentException("Período deve ser 1h, 6h, 24h ou 7d");
        };
        return historyRepository.findByTerminalIdTerminalAndCapturedAtGreaterThanEqualOrderByCapturedAtAsc(
                terminalId, Instant.now().minus(duration)).stream()
                .map(TerminalTelemetryHistoryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<TerminalTelemetryResponse.AlertResponse> alerts(String terminalId) {
        tenantTerminal(terminalId);
        return alertRepository.findTop100ByTerminalIdTerminalOrderByOpenedAtDesc(terminalId)
                .stream().map(TerminalTelemetryResponse.AlertResponse::from).toList();
    }

    private TerminalTelemetryResponse detailFor(TerminalTelemetryCurrent current, boolean online) {
        var alerts = alertRepository.findByTerminalIdTerminalAndStatusOrderByOpenedAtDesc(
                current.getTerminalId(), TelemetryAlertStatus.ACTIVE);
        return TerminalTelemetryResponse.from(current, online,
                online ? current.getOperationalStatus() : TerminalOperationalStatus.OFFLINE, alerts);
    }

    private Terminal tenantTerminal(String id) {
        return terminalRepository.findByIdTerminalAndCondominioEmpresaId(id, EmpresaContext.require())
                .orElseThrow(TerminalNotFoundException::new);
    }
    private long count(List<TerminalTelemetryResponse> items, TerminalOperationalStatus status) {
        return items.stream().filter(item -> item.status() == status).count();
    }

    private TerminalTelemetryMetrics map(TerminalTelemetryRequest r) {
        var m = new TerminalTelemetryMetrics();
        var s = r.system();
        m.setSystemUptimeSeconds(s.uptimeSeconds()); m.setCpuUsagePercent(s.cpuUsagePercent());
        m.setCpuTemperatureCelsius(s.cpuTemperatureCelsius()); m.setMemoryUsedBytes(s.memoryUsedBytes());
        m.setMemoryTotalBytes(s.memoryTotalBytes()); m.setMemoryUsagePercent(s.memoryUsagePercent());
        m.setDiskUsedBytes(s.diskUsedBytes()); m.setDiskTotalBytes(s.diskTotalBytes());
        m.setDiskUsagePercent(s.diskUsagePercent()); m.setLoadAverage1m(s.loadAverage1m());
        m.setUndervoltageNow(s.undervoltageNow()); m.setUndervoltageOccurred(s.undervoltageOccurred());
        m.setThrottledNow(s.throttledNow()); m.setThrottledOccurred(s.throttledOccurred());
        m.setFrequencyCappedNow(s.frequencyCappedNow()); m.setFrequencyCappedOccurred(s.frequencyCappedOccurred());
        m.setSoftTemperatureLimitNow(s.softTemperatureLimitNow());
        m.setSoftTemperatureLimitOccurred(s.softTemperatureLimitOccurred()); m.setThrottledRaw(s.throttledRaw());
        var n = r.network();
        m.setNetworkConnected(n.connected()); m.setInterfaceName(n.interfaceName()); m.setWifiSsid(n.ssid());
        m.setLocalIp(n.localIp()); m.setWifiSignalPercent(n.wifiSignalPercent());
        m.setWifiSignalQuality(n.wifiSignalQuality()); m.setBackendReachable(n.backendReachable());
        m.setBackendLatencyMs(n.backendLatencyMs());
        var a = r.application();
        m.setAppVersion(a.version()); m.setAppUptimeSeconds(a.uptimeSeconds());
        m.setWebsocketStatus(a.websocketStatus()); m.setLastProductSyncStartedAt(a.lastProductSyncStartedAt());
        m.setLastProductSyncCompletedAt(a.lastProductSyncCompletedAt());
        m.setLastSuccessfulSyncAt(a.lastSuccessfulSyncAt()); m.setLastSyncError(a.lastSyncError());
        m.setPurchaseActive(a.purchaseActive()); m.setPaymentInProgress(a.paymentInProgress());
        var d = r.display();
        m.setDisplayWidth(d.width()); m.setDisplayHeight(d.height()); m.setDisplayOrientation(d.orientation());
        return m;
    }

    private TerminalTelemetryMetrics copy(TerminalTelemetryMetrics source) {
        var target = new TerminalTelemetryMetrics();
        org.springframework.beans.BeanUtils.copyProperties(source, target);
        return target;
    }
}
