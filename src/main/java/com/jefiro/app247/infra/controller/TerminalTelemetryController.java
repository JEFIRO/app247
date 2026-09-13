package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.dto.*;
import com.jefiro.app247.domain.model.enum_type.TerminalOperationalStatus;
import com.jefiro.app247.infra.service.TerminalTelemetryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
public class TerminalTelemetryController {
    private final TerminalTelemetryService service;
    public TerminalTelemetryController(TerminalTelemetryService service) { this.service = service; }

    @GetMapping("/terminal/health")
    public Map<String, Object> health() { return Map.of("status", "UP", "timestamp", Instant.now()); }

    @PostMapping("/terminal/telemetry")
    public ResponseEntity<TerminalTelemetryResponse> receive(
            @RequestBody @Valid TerminalTelemetryRequest request) {
        return ResponseEntity.accepted().body(service.receive(request));
    }

    @GetMapping("/terminais/monitoramento")
    public TerminalMonitoringDashboardResponse dashboard(
            @RequestParam(required = false) String condominioId,
            @RequestParam(required = false) TerminalOperationalStatus status) {
        return service.dashboard(condominioId, status);
    }

    @GetMapping("/terminais/{id}/telemetria")
    public TerminalTelemetryResponse detail(@PathVariable String id) { return service.detail(id); }

    @GetMapping("/terminais/{id}/telemetria/historico")
    public List<TerminalTelemetryHistoryResponse> history(@PathVariable String id,
                                                          @RequestParam(defaultValue = "1h") String period) {
        return service.history(id, period);
    }

    @GetMapping("/terminais/{id}/telemetria/alertas")
    public List<TerminalTelemetryResponse.AlertResponse> alerts(@PathVariable String id) {
        return service.alerts(id);
    }
}
