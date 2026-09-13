package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.enum_type.TelemetryAlertStatus;
import com.jefiro.app247.infra.service.admin.AdminActivityQueryService;
import com.jefiro.app247.infra.service.admin.AdminAlertQueryService;
import com.jefiro.app247.infra.service.admin.AdminDashboardService;
import com.jefiro.app247.infra.service.admin.AdminOnboardingService;
import com.jefiro.app247.infra.service.admin.AdminPaymentQueryService;
import com.jefiro.app247.infra.service.admin.AdminSalesQueryService;
import com.jefiro.app247.infra.service.admin.AdminTerminalQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/admin")
public class AdminController {
    @Autowired
    private AdminDashboardService dashboardService;
    @Autowired
    private AdminOnboardingService onboardingService;
    @Autowired
    private AdminTerminalQueryService terminalService;
    @Autowired
    private AdminAlertQueryService alertService;
    @Autowired
    private AdminPaymentQueryService paymentService;
    @Autowired
    private AdminSalesQueryService salesService;
    @Autowired
    private AdminActivityQueryService activityService;

    @GetMapping("/dashboard")
    public ResponseEntity<?> dashboard() {
        return ResponseEntity.ok(dashboardService.dashboard());
    }

    @GetMapping("/onboarding-status")
    public ResponseEntity<?> onboarding() {
        return ResponseEntity.ok(onboardingService.status());
    }

    @GetMapping("/terminals/summary")
    public ResponseEntity<?> terminals() {
        return ResponseEntity.ok(terminalService.summary());
    }

    @GetMapping("/alerts/summary")
    public ResponseEntity<?> alertSummary() {
        return ResponseEntity.ok(alertService.summary());
    }

    @GetMapping("/alerts")
    public ResponseEntity<?> alerts(@RequestParam(required = false) TelemetryAlertStatus status,
                                    Pageable pageable) {
        return ResponseEntity.ok(alertService.list(status, pageable));
    }

    @GetMapping("/payments/attention-summary")
    public ResponseEntity<?> paymentAttention() {
        return ResponseEntity.ok(paymentService.attention());
    }

    @GetMapping("/payments")
    public ResponseEntity<?> payments(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String terminalId,
            @RequestParam(required = false) String orderId,
            Pageable pageable) {
        return ResponseEntity.ok(paymentService.list(
                from, to, status, provider, terminalId, orderId, pageable));
    }

    @GetMapping("/sales/summary")
    public ResponseEntity<?> salesSummary(
            @RequestParam(defaultValue = "TODAY") String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(salesService.summary(period, from, to));
    }

    @GetMapping("/sales")
    public ResponseEntity<?> sales(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String condominioId,
            @RequestParam(required = false) String terminalId,
            @RequestParam(required = false) String status,
            Pageable pageable) {
        return ResponseEntity.ok(salesService.list(
                period, from, to, condominioId, terminalId, status, pageable));
    }

    @GetMapping("/activity")
    public ResponseEntity<?> activity(@RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(activityService.recent(size));
    }
}
