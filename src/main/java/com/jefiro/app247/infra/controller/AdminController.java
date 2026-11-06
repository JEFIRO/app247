package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.enum_type.TelemetryAlertStatus;
import com.jefiro.app247.infra.service.admin.AdminActivityQueryService;
import com.jefiro.app247.infra.service.admin.AdminAlertQueryService;
import com.jefiro.app247.infra.service.admin.AdminDashboardService;
import com.jefiro.app247.infra.service.admin.AdminOnboardingService;
import com.jefiro.app247.infra.service.admin.AdminPaymentQueryService;
import com.jefiro.app247.infra.service.admin.AdminSalesQueryService;
import com.jefiro.app247.infra.service.admin.AdminSaleDetailService;
import com.jefiro.app247.domain.model.dto.admin.AdminSaleDetailResponse;
import com.jefiro.app247.domain.model.dto.admin.AdminSendReceiptRequest;
import com.jefiro.app247.infra.service.comprovante.ComprovanteEnvioResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import com.jefiro.app247.infra.service.admin.AdminTerminalQueryService;
import com.jefiro.app247.infra.service.PointPaymentRefundService;
import com.jefiro.app247.domain.model.dto.admin.AdminRefundResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;

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
    @Autowired
    private PointPaymentRefundService refundService;
    @Autowired
    private AdminSaleDetailService saleDetailService;

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

    @PostMapping("/orders/{orderId}/refund")
    @Operation(summary = "Solicita reembolso integral de uma Order Point aprovada",
            description = "Operação tenant-aware e idempotente. Persiste a intenção antes de chamar POST /v1/orders/{id}/refund com body vazio.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reembolso confirmado ou já confirmado"),
            @ApiResponse(responseCode = "401", description = "JWT ausente ou inválido"),
            @ApiResponse(responseCode = "403", description = "Usuário sem papel ADMIN/GERENTE"),
            @ApiResponse(responseCode = "404", description = "PURCHASE_NOT_FOUND"),
            @ApiResponse(responseCode = "409", description = "REFUND_NOT_ALLOWED ou REFUND_REMOTE_ORDER_MISSING"),
            @ApiResponse(responseCode = "502", description = "Provedor não confirmou; intenção permanece para reconciliação")
    })
    public ResponseEntity<AdminRefundResponse> refund(@PathVariable String orderId) {
        return ResponseEntity.ok(refundService.refund(orderId));
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

    @GetMapping("/sales/{orderId}")
    @Operation(summary = "Detalhe de uma venda da empresa: itens, pagamento e reembolso",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<AdminSaleDetailResponse> saleDetail(@PathVariable String orderId) {
        return ResponseEntity.ok(saleDetailService.detail(orderId));
    }

    @PostMapping("/sales/{orderId}/comprovante")
    @Operation(summary = "Envia o comprovante de uma venda aprovada por WhatsApp ou e-mail",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Envio aceito ou já processado"),
            @ApiResponse(responseCode = "400", description = "Destinatário inválido"),
            @ApiResponse(responseCode = "404", description = "PURCHASE_NOT_FOUND"),
            @ApiResponse(responseCode = "422", description = "Venda não aprovada")
    })
    public ResponseEntity<ComprovanteEnvioResponse> sendReceipt(
            @PathVariable String orderId, @Valid @RequestBody AdminSendReceiptRequest request) {
        return ResponseEntity.ok(saleDetailService.sendReceipt(orderId, request));
    }

    @GetMapping("/activity")
    public ResponseEntity<?> activity(@RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(activityService.recent(size));
    }
}
