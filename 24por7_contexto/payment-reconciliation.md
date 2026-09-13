# Reconciliação de pagamentos Point

Voltar para [[00-index]]. Integração principal em [[mercado-pago]], contrato em [[api]] e notificações em [[websocket]].

## Responsabilidades e fonte de verdade

O webhook permanece o caminho principal e de menor latência. `PaymentReconciliationService` é o fallback único para consultar uma cobrança já criada quando o webhook foi perdido ou o backend estava offline. O WebSocket apenas notifica; `GET /order/{orderId}/status?terminalId={terminalId}` recupera uma Order conhecida e `GET /pagamento/terminal/{terminalId}/ativo` descobre a tentativa não resolvida após reinício. O banco mantém o último estado confirmado e o Mercado Pago é a fonte externa do resultado final.

```text
webhook recebido -> PaymentStateTransitionService -> transação -> evento AFTER_COMMIT

webhook perdido/backend offline
  -> startup, scheduler ou endpoint
  -> PaymentReconciliationService
  -> GET Mercado Pago /v1/orders/{Order.mpOrderId}
  -> PaymentStateTransitionService
  -> transação -> evento AFTER_COMMIT
```

O identificador remoto é persistido em `PaymentAttempt.providerOrderId` (com acesso legado por `Order.getMpOrderId`). `external_reference` identifica a tentativa, não apenas a Order, e possui unicidade por provider. Respostas com ID remoto ou referência divergentes são rejeitadas. A credencial é resolvida exclusivamente por `Order.empresa -> MercadoPagoConta.accessToken`.

## Estados e transições

São reconciliáveis `PENDING`, `CREATED`, `AT_TERMINAL` e `ACTION_REQUIRED`. `PROCESSED`, `FAILED`, `CANCELED`, `EXPIRED` e `REFUNDED` não geram consulta sob demanda. O mapeamento continua centralizado em `MercadoPagoStatusMapper`:

| Mercado Pago | PaymentAttempt | Terminal |
|---|---|---|
| `created`, `at_terminal` | `PENDING` | `WAITING_PAYMENT` |
| `processed` | `PROCESSED` | `APPROVED` |
| `failed` | `FAILED` | `REJECTED` |
| `canceled` | `CANCELED` | `CANCELLED` |
| `expired` | `EXPIRED` | `EXPIRED` |
| `action_required` | `ACTION_REQUIRED` | `ACTION_REQUIRED` |
| `refunded` | `REFUNDED` | `REFUNDED` |

`PaymentStateTransitionService` é usado pelo webhook e pela reconciliação. Ele carrega a Order com `PESSIMISTIC_WRITE`, valida versão e `OrderStatus.canTransitionTo`, portanto um evento atrasado não rebaixa `PROCESSED` para estado intermediário ou falha. Duas threads que consultaram o mesmo estado remoto serializam na Order; somente a primeira mudança publica efeitos de negócio.

Estoque não sofre nova baixa: a criação da Order já reserva a quantidade; aprovação confirma a reserva com chave persistida `orderId:itemId:VENDA`. Repetição da aprovação não publica novo `OrderPaidEvent`, e a constraint/chave de `movimentacao_estoque` também protege o efeito permanentemente.

## Gatilhos e resiliência

- Startup: `ApplicationReadyEvent` chama o mesmo reconciliador e nunca impede a aplicação de subir.
- Scheduler: a cada 15 segundos, com atraso inicial de 15 segundos, processa no máximo 100 IDs isoladamente.
- Janela: apenas Orders criadas nas últimas 4 horas. A cobrança padrão expira em 15 minutos (`PT15M`); quatro horas cobrem atraso operacional e o limite máximo documentado da Point sem varrer pendências históricas.
- Endpoint: valida que a Order pertence ao `terminalId`, consulta o Mercado Pago apenas em estado reconciliável e retorna o estado local com `reconciled=false` se a consulta estiver indisponível. Timeout HTTP nunca vira recusa.
- Descoberta: o endpoint por Terminal seleciona a tentativa não resolvida mais antiga, registra erro de integridade se houver mais de uma e reutiliza a mesma reconciliação do endpoint por Order.
- Nova chamada de cobrança: se o carrinho já tem Order com `mpOrderId`, reconcilia primeiro. Mesmo se a consulta falhar, reutiliza a mesma Order/cobrança e a chave de idempotência original; não cria outra.

A Order, o carrinho e uma `PaymentAttempt PENDING` são confirmados atomicamente por `PointPaymentPersistenceService.prepare` em uma transação `REQUIRES_NEW`. Somente depois do commit `PagamentoService` chama o Mercado Pago fora de transação. A resposta remota é gravada por `persistRemoteAcceptance` em uma segunda transação `REQUIRES_NEW`. Não existe listener de criação executando JPA em `AFTER_COMMIT`; `AFTER_COMMIT` permanece apenas para notificações sem gravação financeira.

Se um webhook chegar entre o HTTP 201 e a segunda transação, ele já encontra a tentativa local pela referência externa. O webhook pode anexar o ID remoto à tentativa e aplicar o estado; a persistência posterior de `created` não regride um estado mais novo como `CANCELED` ou `PROCESSED`.

Se o POST remoto terminar com timeout antes de o backend persistir a resposta, a tentativa continua `PENDING` sem `providerOrderId`: isso significa resultado desconhecido, não rejeição. Após o cooldown, startup, scheduler ou status query repetem o mesmo POST com o `idempotencyKey` já persistido. `PointPaymentSubmissionService` serializa a operação por Order na instância; a chave idempotente e suas constraints são a barreira entre instâncias. Cada submissão atualiza `PaymentAttempt.updatedAt` antes da chamada para impedir polling externo agressivo. Se a repetição devolver uma Order já terminal, o ID remoto é persistido e o estado passa pela mesma máquina monotônica.

Uma Order em estado de pagamento sem tentativa é violação de integridade: gera log `ORDER_WITHOUT_PAYMENT` e não recebe tentativa retroativa silenciosa. O scheduler seleciona Orders intermediárias que já possuem tentativa, inclusive as que ainda não têm ID remoto, usando `exists` compatível com MySQL 8.

Configuração: `PAYMENT_RECONCILIATION_DELAY_MS`, `PAYMENT_RECONCILIATION_INITIAL_DELAY_MS`, `PAYMENT_RECONCILIATION_WINDOW_HOURS`, `PAYMENT_RECONCILIATION_BATCH_SIZE` e `PAYMENT_SUBMISSION_RETRY_COOLDOWN_MS`.

## Idempotency-Key e cardinalidade

O modelo é `Carrinho 1 -> 0..1 Order 1 -> N PaymentAttempt`. Cada tentativa persiste `attemptNumber`, `idempotencyKey` e `externalReference`; repetição HTTP da mesma tentativa reutiliza esses valores. Retry futuro cria uma nova tentativa e nunca reabre uma encerrada. O fluxo atual inicia apenas a primeira tentativa, mas o schema já admite tentativas e valores adicionais.

## Contrato do status

Os dois endpoints de recuperação devolvem `PaymentStatusResponse` sem credenciais. A descoberta por Terminal responde `204` quando não existe tentativa ativa:

```json
{
  "type": "PAYMENT_STATUS",
  "orderId": "uuid-local",
  "cartId": "uuid-carrinho",
  "paymentId": "uuid-local",
  "paymentAttemptId": "uuid-local",
  "terminalId": "uuid-terminal",
  "status": "APPROVED",
  "mercadoPagoStatus": "processed",
  "transactionId": "id-do-pagamento-remoto",
  "statusDetail": "accredited",
  "message": "Pagamento aprovado",
  "amount": 10.00,
  "updatedAt": "2026-08-28T01:00:00",
  "reconciled": true
}
```

`reconciled=true` significa que o Mercado Pago foi consultado nessa requisição; não significa aprovação. Em falha temporária, o status permanece `WAITING_PAYMENT` e `reconciled=false`.

## Validação

Testes automatizados cobrem aprovação, recusa, cancelamento, permanência pendente, estado final sem consulta, indisponibilidade externa, monotonicidade, repetição de aprovação, isolamento de falha no lote, descoberta no startup, repetição idempotente sem ID remoto e cem chamadas concorrentes gerando uma submissão remota. O teste físico com Point e o bloqueio real do webhook exigem ambiente/credenciais externos e devem seguir [[payment-recovery]] antes da liberação em produção.
