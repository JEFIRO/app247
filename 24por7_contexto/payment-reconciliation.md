# Reconciliação de pagamentos Point

Voltar para [[00-index]]. Integração principal em [[mercado-pago]], contrato em [[api]] e notificações em [[websocket]].

## Responsabilidades e fonte de verdade

O webhook permanece o caminho principal e de menor latência. `PaymentReconciliationService` é o fallback único para consultar uma cobrança já criada quando o webhook foi perdido ou o backend estava offline. O WebSocket apenas notifica; `GET /order/{orderId}/status?terminalId={terminalId}` recupera o estado sob demanda. O banco mantém o último estado confirmado e o Mercado Pago é a fonte externa do resultado final.

```text
webhook recebido -> PaymentStateTransitionService -> transação -> evento AFTER_COMMIT

webhook perdido/backend offline
  -> startup, scheduler ou endpoint
  -> PaymentReconciliationService
  -> GET Mercado Pago /v1/orders/{Order.mpOrderId}
  -> PaymentStateTransitionService
  -> transação -> evento AFTER_COMMIT
```

O identificador remoto é `Order.mpOrderId`, retornado pela criação Point. `external_reference` deve coincidir com `Order.idOrder`; respostas com ID remoto ou referência divergentes são rejeitadas. A credencial é resolvida exclusivamente por `Order.empresa -> MercadoPagoConta.accessToken`. O vínculo físico permanece em `Order/Carrinho -> Terminal -> mercadoPagoTerminalId` e nenhuma credencial chega ao Terminal Python.

## Estados e transições

São reconciliáveis `PENDING`, `CREATED`, `AT_TERMINAL` e `ACTION_REQUIRED`. `PROCESSED`, `FAILED`, `CANCELED`, `EXPIRED` e `REFUNDED` não geram consulta sob demanda. O mapeamento continua centralizado em `MercadoPagoStatusMapper`:

| Mercado Pago | Pagamento | Terminal |
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
- Janela: apenas Orders criadas nas últimas 4 horas. A cobrança padrão expira em 5 minutos; quatro horas cobrem atraso operacional e o limite máximo documentado da Point sem varrer pendências históricas.
- Endpoint: valida que a Order pertence ao `terminalId`, consulta o Mercado Pago apenas em estado reconciliável e retorna o estado local com `reconciled=false` se a consulta estiver indisponível. Timeout HTTP nunca vira recusa.
- Nova chamada de cobrança: se o carrinho já tem Order com `mpOrderId`, reconcilia primeiro. Mesmo se a consulta falhar, reutiliza a mesma Order/cobrança e a chave de idempotência original; não cria outra.

Configuração: `PAYMENT_RECONCILIATION_DELAY_MS`, `PAYMENT_RECONCILIATION_INITIAL_DELAY_MS`, `PAYMENT_RECONCILIATION_WINDOW_HOURS` e `PAYMENT_RECONCILIATION_BATCH_SIZE`.

## Idempotency-Key e cardinalidade

O modelo real permanece `Carrinho 1 -> 0..1 Order 1 -> 0..1 Pagamento`. A criação Point usa `X-Idempotency-Key = Order.idOrder`. Repetições da mesma tentativa reutilizam `mpOrderId` e a mesma chave. Uma nova Order — permitida somente pelo fluxo atual após resultado definitivo — recebe outro UUID e outra chave. Não se cria chave aleatória antes de reconciliar uma tentativa existente.

## Contrato do status

`GET /order/{orderId}/status?terminalId={terminalId}` devolve `PaymentStatusResponse` sem credenciais:

```json
{
  "type": "PAYMENT_STATUS",
  "orderId": "uuid-local",
  "paymentId": "uuid-local",
  "terminalId": "uuid-terminal",
  "status": "APPROVED",
  "mercadoPagoStatus": "processed",
  "transactionId": "id-do-pagamento-remoto",
  "statusDetail": "accredited",
  "message": "Pagamento aprovado",
  "updatedAt": "2026-08-28T01:00:00",
  "reconciled": true
}
```

`reconciled=true` significa que o Mercado Pago foi consultado nessa requisição; não significa aprovação. Em falha temporária, o status permanece `WAITING_PAYMENT` e `reconciled=false`.

## Validação

Testes automatizados cobrem aprovação, recusa, cancelamento, permanência pendente, estado final sem consulta, indisponibilidade externa, monotonicidade, repetição de aprovação, isolamento de falha no lote e recuperação do Terminal em reconexão. O teste físico com Point e o bloqueio real do webhook exigem ambiente/credenciais externos e devem seguir o roteiro operacional da tarefa antes da liberação em produção.
