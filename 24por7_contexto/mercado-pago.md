# Integração Mercado Pago

Voltar para [[00-index]]. Persistência em [[database]], autenticação em [[autenticacao]] e notificações em [[websocket]].

## Escopo implementado

O backend contém OAuth por empresa, vínculo de maquininhas Point a terminais internos, criação e consulta de orders Point, recepção autenticada de webhooks, processamento assíncrono e simuladores. O SDK também contém código para PIX e Checkout Pro, mas esses dois métodos não estão ligados a controllers.

## Configuração sem credenciais no código

| Variável | Propriedade | Uso |
|---|---|---|
| `ACCESS_TOKEN_MP` | `api.mercado.pago.access.token` | Fluxos antigos do SDK. |
| `MP_CLIENT_SECRET` | `spring.mp.id.secret` | Troca OAuth. |
| `MP_WEBHOOK_SECRET` | `api.mercado.pago.webhook.secret` | HMAC das notificações. |
| `MP_TEST_ACCESS_TOKEN` | `api.mercado.pago.test.access.token` | Endpoints de simulação. |
| `MP_TEST_TERMINAL_ID` | `api.mercado.pago.test.terminal-id` | Terminal de teste; padrão `NEWLAND_N950__SBX0000001`. |

Os testes externos falham explicitamente quando `MP_TEST_ACCESS_TOKEN` não está configurado. O endpoint de webhook responde `503` enquanto `MP_WEBHOOK_SECRET` estiver vazio.

## OAuth e conta por empresa

`GET /mercado-pago/oauth` exige principal `ADMIN` ou `GERENTE`. O usuário e a empresa vêm da autenticação, nunca de um ID confiado ao cliente. O state armazenado no Redis por dez minutos contém ambos os IDs. No callback público, o service confirma que o gestor ainda pertence à mesma empresa e ainda possui papel administrativo antes de trocar o code. Falhas de domínio deixam somente seu código seguro em `oauth:mp:result:{empresaId}` por dez minutos; `GET /mercado-pago/oauth/result` permite ao Flutter da mesma Empresa apresentar o resultado ao retomar, sem credenciais nem identificação cross-tenant.

`MercadoPagoConta` é agora o vínculo histórico Empresa/conta externa. `mpUserId` preserva o `user_id` devolvido pelo token exchange e `mercado_pago_conta_ativa` garante no banco no máximo um vínculo ativo por conta real e um por Empresa. Reautorização da mesma conta atualiza tokens sem duplicar; substituição cria novo vínculo e encerra o anterior. Unlink remove o lease, limpa tokens e permite reutilização futura da conta por outra Empresa. O modelo completo está em [[mercado-pago-account-lifecycle]].

Access token, refresh token, public key, token type, scope, live mode e expiração são lidos de `MercadoPagoTokenResponse`. Antes de usar uma conta, o service verifica `dataExpiracao`; token expirado exige nova autorização OAuth. Renovação automática continua não implementada.

As rotas antigas `GET /mp/oauth/mercadopago/{idUser}` e `GET /mp/oauth/terminal/{idUser}` permanecem como aliases administrativos e ignoram o ID arbitrário. O antigo `POST` que aceitava `TerminalResponse` foi removido.

## Maquininhas Point

`GET /mercado-pago/status` fornece o estado agregado usado pela Home do aplicativo gestor. A empresa vem de `EmpresaContext`, sem `empresaId` informado pelo cliente. `contaVinculada` exige lease e vínculo `ACTIVE`, token preenchido e expiração futura; `maquininhaVinculada` exige `TerminalPointBinding ACTIVE` da mesma conta. `configuracaoCompleta` é a conjunção dessas condições. O response inclui contagens e estado (`CONTA_NAO_VINCULADA`, `CONTA_VINCULADA_SEM_POINT`, `POINT_CONFIGURADA`, `CONTA_REVOGADA` ou `CONTA_COM_ERRO`), mas nenhuma credencial. A consulta não chama a API externa.

`GET /mercado-pago/terminais` resolve `EmpresaContext -> MercadoPagoConta -> accessToken` e consulta `GET https://api.mercadopago.com/terminals/v1/list`. A resposta expõe somente os campos já mapeados da API (`id`, `posId`, `store`, `externalPosId`, `operationMode`) e acrescenta `vinculado` e `terminalInternoId` a partir do banco local. Nenhuma credencial é retornada.

`PUT /terminais/{terminalId}/mercado-pago` recebe somente `mercadoPagoTerminalId`. O fluxo:

1. exige gestor autenticado e obtém a empresa do contexto;
2. exige uma conta OAuth dessa empresa;
3. busca o terminal interno por `id + condominio.empresa.id`;
4. lista as maquininhas usando o access token da empresa;
5. confirma que o ID solicitado aparece na resposta externa;
6. confirma que outro terminal interno não usa o mesmo ID;
7. persiste `Terminal.mercadoPagoTerminalId`.

`DELETE /terminais/{terminalId}/mercado-pago` remove o vínculo Point pelo tenant mesmo quando a conta já não está autorizada. `terminal_point_binding` preserva o histórico; `Terminal.mercadoPagoTerminalId` é somente a projeção ativa. Unlink ou substituição da conta encerra todas as Points antigas e não reseta cadastro, catálogo ou ativação do Terminal.

## Criação da order Point

`POST /pagamento/terminal/{carrinho_id}` executa três fases: (A) transação local para criar/reutilizar Order e `PaymentAttempt PENDING`; (B) chamada externa fora de transação; (C) nova transação local para persistir IDs, status e metadados remotos na tentativa. `GET` no mesmo path permanece como alias legado. A chamada externa é `POST https://api.mercadopago.com/v1/orders`. Antes da chamada, o service exige:

```text
Order.empresa -> mercado_pago_conta_ativa -> MercadoPagoConta ACTIVE
Order.idTerminal -> Terminal -> TerminalPointBinding ACTIVE
Point binding pertence à mesma conta ativa e Terminal.condominio.empresa == Order.empresa
```

Se houver `EmpresaContext`, ele também deve coincidir com a empresa da order. Terminal ausente, terminal de outro tenant, conta ausente ou maquininha não vinculada impedem a chamada externa.

Antes de criar ou reutilizar a cobrança, cada Produto do carrinho é recarregado pela chave composta lógica `produtoId + empresaId`. Isso impede que uma referência parcial ou desanexada produza `OrderItem` sem o `codigo_interno` real. `CarrinhoService.validarParaPagamento` também exige ao menos um item, snapshots de preço e quantidade válidos, subtotal positivo exatamente igual à soma dos itens e a mesma empresa em carrinho, produto, item e cadeia `Terminal -> Condomínio -> Empresa`. O snapshot recusa SKU, nome, unidade, quantidade ou valores obrigatórios incompletos antes do INSERT, da reserva e da chamada externa.

O request contém:

- `type: point`;
- `external_reference`: referência única e persistida da tentativa;
- `expiration_time: PT5M` na configuração atual, configurável por `MP_POINT_EXPIRATION_TIME`;
- uma transação com o total;
- `terminal_id` vindo de `Terminal.mercadoPagoTerminalId` e `print_on_terminal: no_ticket`;
- `X-Idempotency-Key`: chave estável e persistida da tentativa;
- valor da transação formatado obrigatoriamente com duas casas decimais.

O carrinho e a linha do Terminal são bloqueados com `PESSIMISTIC_WRITE` na transação A. Somente a requisição que criar a tentativa local recebe autorização para enviar o POST remoto; chamadas concorrentes encontram a tentativa existente e não enviam outra. Outra Order intermediária do mesmo Terminal bloqueia a nova com `PAYMENT_ALREADY_ACTIVE`. Se já existe `providerOrderId`, a mesma cobrança é reconciliada/devolvida. Retry de uma compra futura usa nova Order/tentativa somente depois de a anterior estar terminal.

A criação normal retorna `created` (também é tolerado `at_terminal` por já ser intermediário). Uma repetição idempotente pode devolver estado mais novo, inclusive terminal; nesse caso o ID remoto é persistido e `PaymentStateTransitionService` aplica a resposta sem regressão. O response ao Terminal inclui `paymentAttemptId` e nunca transforma timeout em rejeição.

A resposta é lida por `OrderResponse`. Campos desconhecidos são ignorados, mas `status` e `status_detail` chegam primeiro como strings; só valores conhecidos são convertidos para enums persistidos. Corpo nulo ou ID ausente tornam a criação inválida. Se chegar status futuro, o ID remoto ainda é salvo com estado local conservador `PENDING`; a transição é recusada até o status ser conhecido. `transactions` e `payments` podem estar ausentes/vazios sem causar NPE.

O client HTTP compartilhado possui connect timeout de 5 segundos e read timeout de 15 segundos, ambos configuráveis. Erros e timeouts são convertidos em `ExternalServiceException`; a API devolve `502` sanitizado, sem reproduzir corpo externo sensível. O listener não engole mais exceções, portanto a transação/chamador não aparenta sucesso após falha externa.

O início Point registra marcos `[PAYMENT-BACKEND]` para request recebido, Order/tentativa criada ou reutilizada, chamada e resposta do Mercado Pago, status remoto, tentativa persistida e resposta ao Terminal. IDs operacionais podem aparecer; tokens e credenciais nunca são registrados.

## Ordenação e máquina de estados

`PaymentAttempt.providerEventVersion` e `providerEventAt` preservam o último evento aplicado. `PaymentStateTransitionService` carrega a tentativa por `provider + externalReference` com lock e rejeita versão igual/anterior, regressões de `PROCESSED` e transições inválidas.

Na aprovação, PaymentAttempt e Order recebem `paidAt` e o Carrinho passa a `PAID`. Estados não concluídos encerram o carrinho como `CANCELED` e liberam estoque somente quando há reserva. Cada mudança aceita também anexa um `payment_event` antes da notificação pós-commit.

## Formatos externos separados

O código mantém DTOs distintos para três papéis:

1. `OrderRequest`: comando aceito por `POST /v1/orders`;
2. `OrderResponse`: resposta de criação ou `GET /v1/orders/{id}`;
3. `OrderWebhookNotification`: envelope passivo recebido no webhook.

O endpoint `/v1/orders/{id}/events` usado nos testes recebe apenas comandos de simulação. Nenhum JSON de resposta/webhook é reenviado como comando.

Status externos desconhecidos não são descartados: geram `UnknownExternalStatusException`, passam por três tentativas e terminam em `mp_queue:dlq` para análise. `webhook_event` registra o recebimento antes da fila, deduplica por identidade SHA-256 de ação/Order/versão, mantém payload sanitizado e é atualizado para `QUEUED`, `PROCESSED` ou `DLQ`.

Os simuladores continuam habilitados por padrão no desenvolvimento por `app.test-endpoints.enabled=true`. O profile `prod` os desabilita. Isso não substitui a revisão obrigatória antes de produção.

## Status e mapeamento

Não há status Point `processing` no material externo fornecido. Os estados intermediários documentados são `created`, `at_terminal` e `action_required`.

| Status Mercado Pago | `Order.status` | `PaymentAttempt.status` |
|---|---|---|
| `created` | `CREATED` | `PENDING` |
| `at_terminal` | `AT_TERMINAL` | `PENDING` |
| `processed` | `PROCESSED` | `PROCESSED` e `paidAt` |
| `failed` | `FAILED` | `FAILED` |
| `canceled` | `CANCELED` | `CANCELED` |
| `expired` | `EXPIRED` | `EXPIRED` |
| `refunded` | `REFUNDED` | `REFUNDED` |
| `action_required` | `ACTION_REQUIRED` | `ACTION_REQUIRED` |

O estado é decidido por `status`. `data.status_detail` alimenta o detalhe da order, enquanto `transactions.payments[0].status_detail` alimenta o detalhe do pagamento; na ausência deste último, o detalhe da order é usado como fallback. Assim uma order `failed` pode preservar no pagamento uma causa específica como `bad_filled_card_data`. Um status desconhecido é desserializado sem derrubar o worker, mas não altera nem persiste entidades. Isso evita converter silenciosamente um estado futuro em outro estado interno.

O detalhe de Order continua convertido quando conhecido, enquanto `PaymentAttempt.statusDetail` preserva o texto recebido, inclusive detalhes futuros. Valores documentados reconhecidos incluem `created`, `at_terminal`, `accredited`, `canceled`, `expired`, `refunded`, `check_on_terminal`, `failed` e as recusas `bad_filled_card_data`, `required_call_for_authorize`, `card_disabled`, `high_risk`, `insufficient_amount`, `invalid_installments`, `max_attempts_exceeded`, `rejected_other_reason` e `processing_error`.

## Meio de pagamento

No webhook/resposta, strings externas são convertidas explicitamente:

- `credit_card` → `CREDIT_CARD`;
- `debit_card` → `DEBIT_CARD`;
- `qr` → `PIX`;
- `voucher_card` → `ALIMENT_CARD`.

IDs conhecidos são `amex`, `master`, `visa`, `debmaster`, `debvisa`, `elo`, `diners` e `hipercard`. Um ID desconhecido não impede a atualização do status; o enum interno fica nulo e o texto ainda está disponível no JSON/log de origem, não no banco.

Para simulação de crédito, o código envia `credit_card + visa + installments: 1`. Para débito, envia `debit_card + debvisa`, sem parcelas. `debit_card + visa` é inválido e pode causar `Invalid payment_method_id for the specified payment_method_type`. Para PIX simulado, envia `processed + qr + accredited`, sem `payment_method_id` e sem parcelas.

## Webhook autenticado

Endpoint: `POST /webhook/mercadopago?data.id={orderId}&type=order`.

Antes de enfileirar, o controller:

1. exige `MP_WEBHOOK_SECRET` configurado;
2. extrai `ts` e `v1` de `x-signature`;
3. monta `id:{data.id lowercase};request-id:{x-request-id};ts:{ts};`, omitindo componentes ausentes conforme a regra externa;
4. calcula HMAC-SHA256 e compara em tempo constante;
5. rejeita com `401` assinatura inválida e com `400` divergência entre `data.id` da URL e do body;
6. registra `webhook_event` com identidade SHA-256 de `action + data.id + version` e payload sanitizado;
7. cria no Redis uma chave curta de deduplicação, enfileira em `mp_queue`, marca o inbox `QUEUED` e responde `200`.

Reentregas idênticas respondem `200 DUPLICADO` sem novo item na fila. Se o enqueue falhar, a chave Redis é removida e o inbox permanece `RECEIVED`, permitindo retry. O worker marca `PROCESSED` ou `DLQ` e vincula a tentativa/empresa quando resolvidas. A idempotência do efeito de estoque continua permanente por `movimentacao_estoque.chave_idempotencia`.

## Webhook completo e resumido

Payloads completos carregam `external_reference`, status e possivelmente `transactions.payments`. `refunded` e `action_required` podem omitir `transactions`; todos os campos aninhados são tratados como opcionais.

Uma notificação resumida, como a produzida pela tela de simulação, pode conter apenas `data.id`. Nesse caso `MercadoPagoOrderQueryService` usa `user_id` para localizar a conta OAuth e consulta `GET /v1/orders/{id}`. A resposta consultada fornece `external_reference` e o estado usado na atualização. Se conta, ID ou referência continuarem ausentes, a mensagem falha e o worker a recoloca na fila.

O worker consome a cada dois segundos, move atomicamente o payload para `mp_queue:processing`, tenta no máximo três vezes e envia falhas permanentes para `mp_queue:dlq`. Recuperação automática de itens órfãos em `processing` após queda permanece pendente.

A versão do payload é armazenada em `PaymentAttempt.providerEventVersion`. O processamento usa lock pessimista e `OrderStatus.canTransitionTo`, impedindo que evento antigo ou transição inválida regrida um estado definitivo.

## Eventos

Actions aceitas pela documentação: `order.processed`, `order.canceled`, `order.refunded`, `order.action_required`, `order.failed` e `order.expired`; o DTO também tolera `order.created` e actions futuras como texto. A decisão de estado usa `data.status`, não `action`, evitando duplicar duas fontes de verdade.

`processed` publica `OrderPaidEvent`; falha e expiração publicam `OrderNotCompletedEvent`; cancelamento reutiliza `CompraCanceladaEvent`; reembolso publica evento de liberação com indicação de cancelamento. `EstoqueOrderListener` confirma ou libera a reserva sem novo débito e com idempotência persistente.

Quando o estado muda, `PagamentoService` também publica `PaymentEvent`. Os listeners WebSocket executam após o commit, enviam somente para `Order -> Carrinho -> Terminal` e não revertem o pagamento se o terminal estiver desconectado. O estado continua recuperável por `GET /order/{orderId}/status?terminalId={terminalId}`.

O mapeamento externo foi centralizado em `MercadoPagoStatusMapper`:

| Point | Terminal Python |
|---|---|
| `pending`, `created`, `at_terminal` | `WAITING_PAYMENT` |
| `processed` | `APPROVED` |
| `failed` | `REJECTED` |
| `canceled` | `CANCELLED` |
| `expired` | `EXPIRED` |
| `action_required` | `ACTION_REQUIRED` |
| `refunded` | `REFUNDED` |

## Simulações

`TestarWebhookMP` usa o terminal virtual padrão por configuração e chama `/v1/orders/{id}/events`. Os cenários são comandos permitidos pela documentação: processado em crédito, débito ou QR; falha por fundos insuficientes; cancelado; expirado; reembolsado após processado; e ação requerida.

Os endpoints continuam sob `/api/testes/mercadopago` e continuam públicos pela política geral atual. Não devem ser habilitados em ambiente produtivo sem proteção adicional; veja [[autenticacao]].

## Recuperação sem webhook

A recuperação de pagamentos Point está detalhada em [[payment-reconciliation]]. Webhook continua sendo o caminho principal; startup, scheduler e `GET /order/{orderId}/status?terminalId=...` reutilizam `PaymentReconciliationService` e consultam `Order.mpOrderId`. O endpoint anterior lia somente o banco; agora estados não definitivos são reconciliados antes da resposta. Falha de rede mantém o estado pendente e uma nova chamada de cobrança reutiliza a cobrança anterior depois de tentar reconciliá-la.

## Erros tratados e lacunas

- Criação Point rejeita Order sem empresa/terminal, terminal de outro tenant, maquininha não vinculada, resposta sem ID e status desconhecido.
- HTTP 4xx/5xx e falhas de conexão/timeout são traduzidos para `ExternalServiceException` e resposta sanitizada `502`.
- A falha externa é classificada internamente como autenticação, terminal ausente/não pertencente à conta, cobrança já ativa, conflito de idempotência, payload inválido, timeout, indisponibilidade ou desconhecida. A resposta pública continua sanitizada e o corpo do provedor não é registrado.
- OAuth rejeita state ausente/expirado e gestor que mudou de empresa/papel.
- Webhook rejeita assinatura inválida, segredo ausente e divergência de `data.id`.
- O `RestTemplate` compartilhado possui timeouts configuráveis de conexão e leitura.
- Access token expirado não é renovado automaticamente.
- Status externo futuro é ignorado e não fica em uma DLQ/auditoria permanente.
- Versão externa, lock e máquina de estados protegem monotonicidade.
- Cancelamento Point seguro ainda não possui endpoint de produção. Por isso o Terminal não oferece botão que apenas limpe estado; cancelamento incerto continua bloqueado até existir confirmação remota.

`PT15M` é o padrão documentado da API Orders Point, mas esta aplicação configura `PT5M`; ambos são durações ISO-8601 dentro da faixa oficial de 30 segundos a 3 horas. A resposta inicial oficial é `201` com status `created`, portanto nunca equivale a aprovação. Referências consultadas: [criação de order](https://www.mercadopago.com.br/developers/pt/reference/in-person-payments/point/orders/create-order/post), [status Point](https://www.mercadopago.com.br/developers/pt/docs/mp-point/resources/status-order-transaction) e [notificações](https://www.mercadopago.com.br/developers/pt/docs/mp-point/notifications).
