package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.dto.mercadopago.PreferenceReturn;
import com.jefiro.app247.infra.service.PagamentoService;
import com.jefiro.app247.domain.model.dto.PointPaymentResponse;
import com.jefiro.app247.domain.model.dto.PaymentStatusResponse;
import com.jefiro.app247.infra.service.PaymentReconciliationService;
import com.jefiro.app247.infra.service.PointPaymentCancellationService;
import com.jefiro.app247.infra.service.TerminalRequestAuthorizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController()
@RequestMapping("/pagamento")
@Tag(name = "Pagamento do Terminal", description = "Pagamento Point autenticado e idempotente")
public class PagamentoController {
    private static final Logger log = LoggerFactory.getLogger(PagamentoController.class);
    @Autowired
    PagamentoService service;
    @Autowired
    PaymentReconciliationService reconciliationService;
    @Autowired
    PointPaymentCancellationService cancellationService;
    @Autowired
    TerminalRequestAuthorizationService terminalAuthorization;

    @GetMapping("/terminal/{carrinho_id}")
    @Operation(summary = "Alias legado para iniciar/retomar pagamento Point",
            deprecated = true, security = @SecurityRequirement(name = "terminalToken"))
    public ResponseEntity<?> getPagamento(
            @PathVariable String carrinho_id,
            @RequestHeader(value = "X-Terminal-Token", required = false) String credential,
            @RequestHeader(value = "X-Terminal-Id", required = false) String terminalId) {
        terminalAuthorization.authorize(credential, terminalId);
        service.gerarCobranca(carrinho_id, terminalId);
        return ResponseEntity.ok(true);
    }

    @PostMapping("/terminal/{carrinho_id}")
    @Operation(summary = "Inicia ou retoma a tentativa Point da Order com preço congelado",
            description = "X-Terminal-Token e X-Terminal-Id são obrigatórios. O backend ignora qualquer total externo e usa o snapshot da Order. Repetição da mesma operação retorna a tentativa ativa; retry financeiro após estado terminal cria nova tentativa.",
            security = @SecurityRequirement(name = "terminalToken"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tentativa criada, retomada, já aprovada ou estado UNKNOWN conservado"),
            @ApiResponse(responseCode = "401", description = "Credencial interna inválida"),
            @ApiResponse(responseCode = "404", description = "Carrinho não pertence ao Terminal"),
            @ApiResponse(responseCode = "409", description = "ACTIVE_PAYMENT_ATTEMPT_EXISTS, PAYMENT_ALREADY_ACTIVE, ORDER_PRICE_NOT_FINALIZED ou erro de cupom")
    })
    public ResponseEntity<?> iniciarPagamentoPoint(
            @PathVariable String carrinho_id,
            @Parameter(required = true) @RequestHeader(value = "X-Terminal-Token", required = false) String credential,
            @Parameter(required = true) @RequestHeader(value = "X-Terminal-Id", required = false) String terminalId) {
        terminalAuthorization.authorize(credential, terminalId);
        log.info("[PAYMENT-BACKEND] request recebido cartId={}", carrinho_id);
        PointPaymentResponse response = service.gerarCobranca(carrinho_id, terminalId);
        log.info("[PAYMENT-BACKEND] respondendo Terminal orderId={} status={} mpStatus={}",
                response.orderId(), response.status(), response.mercadoPagoStatus());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/terminal/{terminalId}/ativo")
    @Operation(summary = "Recupera e reconcilia a tentativa não terminal do Terminal",
            description = "UNKNOWN, PENDING, ACTION_REQUIRED e CANCELLING continuam bloqueando nova tentativa.",
            security = @SecurityRequirement(name = "terminalToken"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tentativa ativa encontrada"),
            @ApiResponse(responseCode = "204", description = "Nenhuma tentativa ativa"),
            @ApiResponse(responseCode = "401", description = "Credencial interna inválida"),
            @ApiResponse(responseCode = "403", description = "Terminal inativo ou não operacional")
    })
    public ResponseEntity<PaymentStatusResponse> recuperarPagamentoAtivo(
            @PathVariable String terminalId,
            @RequestHeader(value = "X-Terminal-Token", required = false) String credential) {
        terminalAuthorization.authorize(credential, terminalId);
        return reconciliationService.recoverActiveForTerminal(terminalId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/terminal/order/{orderId}/cancelamento")
    @Operation(summary = "Solicita cancelamento idempotente de uma tentativa Point",
            description = "A intenção CANCELLING é persistida antes da chamada externa. Timeout não é tratado como cancelamento confirmado.",
            security = @SecurityRequirement(name = "terminalToken"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado reconciliado da tentativa"),
            @ApiResponse(responseCode = "401", description = "Credencial interna inválida"),
            @ApiResponse(responseCode = "404", description = "Order não pertence ao Terminal"),
            @ApiResponse(responseCode = "409", description = "Order sem tentativa cancelável")
    })
    public ResponseEntity<PaymentStatusResponse> cancelarPagamentoPoint(
            @PathVariable String orderId,
            @RequestParam String terminalId,
            @RequestHeader(value = "X-Terminal-Token", required = false) String credential) {
        terminalAuthorization.authorize(credential, terminalId);
        return ResponseEntity.ok(cancellationService.cancel(orderId, terminalId));
    }
}
