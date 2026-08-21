package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Order;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TestarWebhookMP {
    private static final Logger log = LoggerFactory.getLogger(TestarWebhookMP.class);

    private final RestTemplate restTemplate;
    private final OrderService orderService;
    private final String accessToken;
    private final String terminalId;

    public TestarWebhookMP(
            RestTemplate restTemplate,
            OrderService orderService,
            @Value("${api.mercado.pago.test.access.token:}") String accessToken,
            @Value("${api.mercado.pago.test.terminal-id:NEWLAND_N950__SBX0000001}") String terminalId
    ) {
        this.restTemplate = restTemplate;
        this.orderService = orderService;
        this.accessToken = accessToken;
        this.terminalId = terminalId;
    }

    // ===================== CRIAÇÃO DE ORDER =====================

    private String criarOrder() {
        return criarOrder(true, "teste_webhook_" + UUID.randomUUID());
    }

    private String criarOrder(boolean tentarLimparAntes) {
        return criarOrder(tentarLimparAntes, "teste_webhook_" + UUID.randomUUID());
    }

    /**
     * @param tentarLimparAntes se true e a criação der 409 (order já na fila
     *                          no terminal), busca e cancela automaticamente
     *                          orders presas (created/at_terminal) nas
     *                          últimas 24h para esse terminal, e tenta criar
     *                          de novo uma única vez.
     * @param externalReference valor a enviar como external_reference da order
     *                          no Mercado Pago. Para o webhook conseguir achar
     *                          a Order local (PagamentoService.atualizarPagamento
     *                          usa orderService.getOrder(external_reference)),
     *                          isso precisa ser o idOrder de uma Order já
     *                          existente no banco.
     */
    private String criarOrder(boolean tentarLimparAntes, String externalReference) {
        validarConfiguracaoDeTeste();
        String createUrl = "https://api.mercadopago.com/v1/orders";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        headers.set("X-Idempotency-Key", UUID.randomUUID().toString());

        String body = """
                {
                  "type": "point",
                  "external_reference": "%s",
                  "expiration_time": "PT2M",
                  "transactions": {
                    "payments": [
                      {
                        "amount": "50.00"
                      }
                    ]
                  },
                  "config": {
                    "point": {
                      "terminal_id": "%s",
                      "print_on_terminal": "no_ticket"
                    },
                    "payment_method": {
                      "default_type": "credit_card",
                      "default_installments": 1,
                      "installments_cost": "seller"
                    }
                  }
                }
                """.formatted(externalReference, terminalId);

        HttpEntity<String> request = new HttpEntity<>(body, headers);

        try {
            // Trocado JsonNode -> Map<String, Object>: o conversor Jackson usado
            // pelo Spring (tools.jackson) não consegue instanciar o tipo abstrato
            // JsonNode do com.fasterxml.jackson.databind, e lançava
            // InvalidDefinitionException. Map é sempre deserializável.
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    createUrl,
                    HttpMethod.POST,
                    request,
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    }
            );

            String orderId = (String) response.getBody().get("id");
            log.info("Order de teste criada: orderId={}, externalReference={}", orderId, externalReference);
            return orderId;

        } catch (HttpClientErrorException.Conflict e) {
            if (!tentarLimparAntes) {
                throw new IllegalStateException(
                        "O terminal continua com uma order na fila mesmo após tentativa de limpeza automática. " +
                                "Resposta da API: " + e.getResponseBodyAsString(),
                        e
                );
            }

            log.info("Terminal de teste possui order pendente; iniciando limpeza controlada");
            int canceladas = limparOrdersTravadasDoTerminal();
            log.info("Orders de teste pendentes canceladas: quantidade={}", canceladas);

            // tenta criar só mais uma vez, sem repetir a limpeza (evita loop infinito)
            return criarOrder(false, externalReference);
        }
    }

    /**
     * Versão pública de limparOrdersTravadasDoTerminal(), para acionar a
     * limpeza manualmente via endpoint, sem precisar esperar um 409.
     */
    public int limparOrdersTravadas() {
        return limparOrdersTravadasDoTerminal();
    }

    /**
     * Busca todas as orders das últimas 24h com status "created" ou
     * "at_terminal" para o terminal configurado e cancela cada uma.
     * Retorna quantas foram efetivamente canceladas.
     */
    private int limparOrdersTravadasDoTerminal() {
        String beginDate = Instant.now().minus(24, ChronoUnit.HOURS).toString();
        String endDate = Instant.now().toString();

        int canceladas = 0;
        for (String status : List.of("created", "at_terminal")) {
            Map<String, Object> resultado = listarOrders(beginDate, endDate, status);
            Object dataObj = resultado.get("data");
            if (!(dataObj instanceof List<?> orders)) {
                continue;
            }

            for (Object item : orders) {
                if (!(item instanceof Map<?, ?> order)) {
                    continue;
                }
                Object config = order.get("config");
                if (!(config instanceof Map<?, ?> configMap)) {
                    continue;
                }
                Object point = configMap.get("point");
                if (!(point instanceof Map<?, ?> pointMap)) {
                    continue;
                }
                Object orderTerminalId = pointMap.get("terminal_id");
                if (!terminalId.equals(orderTerminalId)) {
                    continue; // order de outro terminal, não mexe
                }

                String orderId = (String) order.get("id");
                try {
                    cancelarOrder(orderId);
                    canceladas++;
                } catch (HttpClientErrorException.NotFound ignored) {
                    // já expirou/não existe mais - tudo bem, segue o jogo
                } catch (Exception e) {
                    log.warn("Falha ao cancelar order de teste: orderId={}", orderId, e);
                }
            }
        }
        return canceladas;
    }

    /**
     * Lista orders num intervalo de datas, opcionalmente filtrando por status.
     * begin_date e end_date são obrigatórios pela API (formato RFC 3339,
     * ex: "2026-08-01T00:00:00.000Z"). status é opcional (created, at_terminal,
     * processed, failed, canceled, expired, refunded, action_required).
     */
    public Map<String, Object> listarOrders(String beginDate, String endDate, String status) {
        StringBuilder url = new StringBuilder("https://api.mercadopago.com/v1/orders")
                .append("?begin_date=").append(beginDate)
                .append("&end_date=").append(endDate)
                .append("&type=point")
                .append("&page_size=50")
                .append("&sort_by=created_date")
                .append("&sort_order=desc");

        if (status != null && !status.isBlank()) {
            url.append("&status=").append(status);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url.toString(),
                HttpMethod.GET,
                request,
                new ParameterizedTypeReference<Map<String, Object>>() {
                }
        );

        return response.getBody();
    }

    /**
     * Cancela uma order pelo ID. Funciona para orders em status "created" ou
     * "at_terminal" (as que ainda não foram processadas/finalizadas).
     * Retorna 200 se a order estava "created", ou 202 se estava "at_terminal"
     * (cancelamento assíncrono, confirmado depois por webhook).
     */
    public void cancelarOrder(String orderId) {
        String url = "https://api.mercadopago.com/v1/orders/" + orderId + "/cancel";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        headers.set("X-Idempotency-Key", UUID.randomUUID().toString());

        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Void> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                request,
                Void.class
        );

        log.info("Order de teste cancelada: orderId={}, status={}", orderId, response.getStatusCode());
    }

    // ===================== SIMULAÇÃO GENÉRICA =====================

    /**
     * Envia o evento de simulação de status para uma order.
     *
     * @param orderId  id da order retornada na criação
     * @param bodyJson corpo já formatado em JSON (status + detalhes)
     */
    private void simularStatus(String orderId, String bodyJson) {
        String url = "https://api.mercadopago.com/v1/orders/" + orderId + "/events";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);

        HttpEntity<String> request = new HttpEntity<>(bodyJson, headers);

        ResponseEntity<Void> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                request,
                Void.class
        );

        log.info("Simulação Mercado Pago enviada: orderId={}, status={}", orderId, response.getStatusCode());
    }

    // ===================== CENÁRIOS =====================

    /**
     * Fluxo completo end-to-end: cria uma Order real a partir de um Carrinho
     * existente (mesmo caminho de produção, via OrderService.createOrder),
     * usa o idOrder dela como external_reference no Mercado Pago, e simula o
     * status pedido. Assim o webhook consegue achar a Order local de verdade
     * e o PagamentoService roda o fluxo completo (sem o NoSuchElementException).
     *
     * @param carrinhoId id de um Carrinho já existente com status OPEN
     * @param userId     id do usuário dono do carrinho (pode ser null, igual createOrder aceita)
     * @param cenario    aprovado | sem-fundos | cancelado | expirado | reembolso | acao-requerida
     * @return o idOrder da Order local criada (é o mesmo valor usado como external_reference)
     */
    public String testarComOrderReal(String carrinhoId, String userId, String cenario) {
        Order order = orderService.createOrderTest(carrinhoId, userId);

        String externalReference = order.getIdOrder();

        String mpOrderId = criarOrder(true, externalReference);

        String body = payloadCenario(cenario);

        simularStatus(mpOrderId, body);
        log.info("Order local vinculada à simulação: externalReference={}, mpOrderId={}, scenario={}",
                externalReference, mpOrderId, cenario);
        return externalReference;
    }

    String payloadCenario(String cenario) {
        return switch (cenario) {
            case "aprovado" -> """
                    {
                      "status": "processed",
                      "payment_method_type": "credit_card",
                      "installments": 1,
                      "payment_method_id": "visa",
                      "status_detail": "accredited"
                    }
                    """;

            case "aprovado_pix" -> """
                    {
                      "status": "processed",
                      "payment_method_type": "qr",
                      "status_detail": "accredited"
                    }
                    """;

            case "aprovado_debito" -> """
                    {
                      "status": "processed",
                      "payment_method_type": "debit_card",
                      "payment_method_id": "debvisa",
                      "status_detail": "accredited"
                    }
                    """;
            case "sem-fundos" -> """
                    {
                      "status": "failed",
                      "payment_method_type": "credit_card",
                      "installments": 1,
                      "payment_method_id": "visa",
                      "status_detail": "insufficient_amount"
                    }
                    """;
            case "cancelado" -> """
                    {
                      "status": "canceled"
                    }
                    """;
            case "expirado" -> """
                    {
                      "status": "expired"
                    }
                    """;
            case "acao-requerida" -> """
                    {
                      "status": "action_required"
                    }
                    """;
            default -> throw new IllegalArgumentException("Cenário inválido: " + cenario);
        };
    }

    /**
     * Cria uma order e simula pagamento APROVADO.
     */
    public String testarAprovado() {
        String orderId = criarOrder();
        String body = """
                {
                  "status": "processed",
                  "payment_method_type": "credit_card",
                  "installments": 1,
                  "payment_method_id": "visa",
                  "status_detail": "accredited"
                }
                """;
        simularStatus(orderId, body);
        return orderId;
    }

    /**
     * Cria uma order e simula pagamento RECUSADO por SALDO INSUFICIENTE.
     */
    public String testarSemFundos() {
        String orderId = criarOrder();
        String body = """
                {
                  "status": "failed",
                  "payment_method_type": "credit_card",
                  "installments": 1,
                  "payment_method_id": "visa",
                  "status_detail": "insufficient_amount"
                }
                """;
        simularStatus(orderId, body);
        return orderId;
    }

    /**
     * Cria uma order e simula CANCELAMENTO.
     */
    public String testarCancelado() {
        String orderId = criarOrder();
        String body = """
                {
                  "status": "canceled"
                }
                """;
        simularStatus(orderId, body);
        return orderId;
    }

    /**
     * Cria uma order e simula EXPIRAÇÃO.
     */
    public String testarExpirado() {
        String orderId = criarOrder();
        String body = """
                {
                  "status": "expired"
                }
                """;
        simularStatus(orderId, body);
        return orderId;
    }

    /**
     * Cria uma order já processada (aprovada) e em seguida simula REEMBOLSO.
     * Obrigatório: só é possível reembolsar uma order que já foi "processed".
     */
    public String testarReembolso() {
        String orderId = testarAprovado(); // precisa estar processed antes
        String body = """
                {
                  "status": "refunded"
                }
                """;
        simularStatus(orderId, body);
        return orderId;
    }

    /**
     * Cria uma order e simula AÇÃO REQUERIDA no terminal (pode levar até 40s).
     */
    public String testarAcaoRequerida() {
        String orderId = criarOrder();
        String body = """
                {
                  "status": "action_required"
                }
                """;
        simularStatus(orderId, body);
        return orderId;
    }

    private void validarConfiguracaoDeTeste() {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("MP_TEST_ACCESS_TOKEN não configurado");
        }
    }
}
