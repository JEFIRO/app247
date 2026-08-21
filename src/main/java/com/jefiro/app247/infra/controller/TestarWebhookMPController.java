package com.jefiro.app247.infra.controller;

import com.jefiro.app247.infra.service.TestarWebhookMP;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.Map;

@RestController
@RequestMapping("/api/testes/mercadopago")
@ConditionalOnProperty(name = "app.test-endpoints.enabled", havingValue = "true", matchIfMissing = true)
public class TestarWebhookMPController {

    private final TestarWebhookMP testarWebhookMP;

    public TestarWebhookMPController(TestarWebhookMP testarWebhookMP) {
        this.testarWebhookMP = testarWebhookMP;
    }

    @PostMapping("/aprovado")
    public ResponseEntity<String> testarAprovado() {
        String orderId = testarWebhookMP.testarAprovado();
        return ResponseEntity.ok("Order criada e simulada como APROVADA: " + orderId);
    }

    @PostMapping("/sem-fundos")
    public ResponseEntity<String> testarSemFundos() {
        String orderId = testarWebhookMP.testarSemFundos();
        return ResponseEntity.ok("Order criada e simulada como SEM FUNDOS: " + orderId);
    }

    @PostMapping("/cancelado")
    public ResponseEntity<String> testarCancelado() {
        String orderId = testarWebhookMP.testarCancelado();
        return ResponseEntity.ok("Order criada e simulada como CANCELADA: " + orderId);
    }

    @PostMapping("/expirado")
    public ResponseEntity<String> testarExpirado() {
        String orderId = testarWebhookMP.testarExpirado();
        return ResponseEntity.ok("Order criada e simulada como EXPIRADA: " + orderId);
    }

    @PostMapping("/reembolso")
    public ResponseEntity<String> testarReembolso() {
        String orderId = testarWebhookMP.testarReembolso();
        return ResponseEntity.ok("Order criada, aprovada e simulada como REEMBOLSADA: " + orderId);
    }

    @PostMapping("/acao-requerida")
    public ResponseEntity<String> testarAcaoRequerida() {
        String orderId = testarWebhookMP.testarAcaoRequerida();
        return ResponseEntity.ok("Order criada e simulada como AÇÃO REQUERIDA: " + orderId);
    }

    /**
     * Busca e cancela manualmente todas as orders presas (created/at_terminal)
     * do terminal configurado, nas últimas 24h. Útil pra destravar antes de
     * rodar os testes, sem precisar esperar um 409.
     */
    @PostMapping("/limpar")
    public ResponseEntity<String> limparOrdersTravadas() {
        int canceladas = testarWebhookMP.limparOrdersTravadas();
        return ResponseEntity.ok(canceladas + " order(s) presa(s) cancelada(s).");
    }

    /**
     * Fluxo completo end-to-end: cria a Order local de verdade (a partir de
     * um Carrinho existente) e simula o cenário escolhido em cima dela, para
     * que o webhook consiga achar a Order e o fluxo real de PagamentoService
     * rode ponta a ponta.
     *
     * Body:
     * {
     *   "carrinhoId": "0a6e056f-7dc7-4fa3-9951-03d202102f46",
     *   "userId": "84f2f965-fd7b-49f9-9ab7-0ecd26b50a98",
     *   "cenario": "aprovado"
     * }
     */
    @PostMapping("/teste-real")
    public ResponseEntity<String> testarComOrderReal(@RequestBody TesteRealRequest req) {
        String orderId = testarWebhookMP.testarComOrderReal(req.carrinhoId(), req.userId(), req.cenario());
        return ResponseEntity.ok("Order local " + orderId + " criada e cenário '" + req.cenario() + "' simulado.");
    }

    public record TesteRealRequest(String carrinhoId, String userId, String cenario) {}

    /**
     * Lista orders num intervalo de datas (obrigatório) e status (opcional).
     * Datas em RFC 3339, ex: "2026-08-01T00:00:00.000Z".
     * Ex: GET /api/testes/mercadopago/listar?beginDate=2026-08-01T00:00:00.000Z&endDate=2026-08-09T23:59:59.999Z
     * Ex: GET /api/testes/mercadopago/listar?beginDate=...&endDate=...&status=created (pra achar order travada)
     */
    @GetMapping("/listar")
    public ResponseEntity<Map<String, Object>> listarOrders(
            @RequestParam String beginDate,
            @RequestParam String endDate,
            @RequestParam(required = false) String status
    ) {
        return ResponseEntity.ok(testarWebhookMP.listarOrders(beginDate, endDate, status));
    }

    /**
     * Cancela manualmente uma order pelo ID — use se o terminal ficar
     * travado com "already_queued_order_on_terminal" e você tiver o ID
     * da order presa (ex: capturado no log de uma tentativa anterior).
     * Ex: POST /api/testes/mercadopago/cancelar/ORD0000...
     */
    @PostMapping("/cancelar/{orderId}")
    public ResponseEntity<String> cancelarOrder(@PathVariable String orderId) {
        testarWebhookMP.cancelarOrder(orderId);
        return ResponseEntity.ok("Order " + orderId + " cancelada.");
    }

    /**
     * Endpoint único, útil se preferir escolher o cenário por parâmetro
     * em vez de ter uma rota fixa por cenário.
     * Ex: POST /api/testes/mercadopago/cenario?tipo=sem-fundos
     */
    @PostMapping("/cenario")
    public ResponseEntity<String> testarPorCenario(@RequestParam String tipo) {
        String orderId = switch (tipo) {
            case "aprovado" -> testarWebhookMP.testarAprovado();
            case "sem-fundos" -> testarWebhookMP.testarSemFundos();
            case "cancelado" -> testarWebhookMP.testarCancelado();
            case "expirado" -> testarWebhookMP.testarExpirado();
            case "reembolso" -> testarWebhookMP.testarReembolso();
            case "acao-requerida" -> testarWebhookMP.testarAcaoRequerida();
            default -> throw new IllegalArgumentException("Cenário inválido: " + tipo);
        };
        return ResponseEntity.ok("Cenário '" + tipo + "' simulado. Order: " + orderId);
    }
}
