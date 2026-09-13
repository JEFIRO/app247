# Ciclo de vida do Terminal e factory reset da aplicação

Voltar para [[00-index]]. Conta/Point em [[mercado-pago-account-lifecycle]], WebSocket em [[websocket]] e retenção em [[database-retention]].

## Estados separados

`Terminal.status` continua representando presença (`ONLINE`, `OFFLINE`, `MANUTENCAO`). `Terminal.lifecycleState` representa o vínculo operacional:

- `ACTIVE`: ativação empresarial vigente;
- `RESET_REQUIRED`: encerramento definitivo confirmado e reset ainda pendente;
- `UNACTIVATED`: reset confirmado; registro antigo permanece apenas para histórico.

O bootstrap deriva ainda `PAYMENT_NOT_CONFIGURED` para Terminal ativo sem conta/Point operacional e `DISABLED` para suspensão temporária de Empresa ou Terminal, sem limpeza local.

Não se duplicou o UUID físico. O `terminalId` persistido em `db/terminal.json` é o vínculo empresarial do backend. A identidade física independente já existente é o serial de hardware do Raspberry e, como fallback Linux, `/etc/machine-id`, lidos por `TerminalInfo`; ambos ficam fora dos arquivos apagados.

## Encerramento definitivo da Empresa

`DELETE /empresas/me` exige administrador do tenant. O fluxo é transacional e soft-delete:

1. bloqueia a Empresa;
2. recusa se houver pagamento `PENDING`/`ACTION_REQUIRED`;
3. desvincula a conta Mercado Pago e invalida Points;
4. marca todos os Terminais da hierarquia como `RESET_REQUIRED`, persiste timestamps/motivo e desabilita operação;
5. publica um evento por Terminal;
6. grava `closed_at`, `ativo=false`, AuditLog e desativa os usuários da Empresa.

Orders, itens, tentativas/eventos de pagamento, gateway IDs, vínculos históricos, Terminais antigos e auditoria não são apagados. Empresa apenas suspensa (`ativo=false`, `closed_at=null`) produz `DISABLED` e não solicita factory reset.

## Entrega online e offline

O evento WebSocket nativo é:

```json
{
  "type": "TERMINAL_FACTORY_RESET_REQUIRED",
  "terminalId": "...",
  "state": "RESET_REQUIRED",
  "reason": "COMPANY_CLOSED",
  "requestedAt": "..."
}
```

Ele acelera a descoberta, mas não autoriza a limpeza sozinho. No startup, em cada reconnect do socket de pagamento e periodicamente, o Terminal consulta `GET /terminal/{terminalId}/bootstrap`.

Somente HTTP 200 com JSON válido e `state=RESET_REQUIRED` executa o fluxo. Timeout, offline, HTTP 5xx, JSON inválido ou estado desconhecido não provocam reset. Um `terminalId` antigo que não existe mais recebe `RESET_REQUIRED/TERMINAL_NOT_FOUND` como confirmação autoritativa, em vez de depender de erro genérico.

O Terminal tenta `POST .../factory-reset/started`, grava um marcador atômico e encerra. No próximo processo, a limpeza ocorre antes de carregar telas, SQLite, sync ou serviços. Ao terminar, grava um recibo durável com o ID antigo. A tela de ativação repete `POST .../factory-reset/completed` até receber 2xx; só então remove o recibo. O backend muda o registro antigo para `UNACTIVATED` e libera serial/MAC da ativação empresarial antiga.

## `FACTORY_RESET_APPLICATION`

O reset remoto remove:

- `db/terminal.json`: ativação, Empresa, Condomínio e terminalId operacional;
- `db/terminal.db`: produtos, códigos de barras, promoções/materialização comercial e `catalog_sync_state`;
- `database/last_sync.txt`: cursor legado;
- `temp_checkout.png`: artefato temporário de pagamento.

A remoção usa staging identificado pelo reset. O marcador permanece até o conjunto terminar; uma queda no meio faz o próximo startup retomar a operação. O staging remoto é destruído ao concluir, evitando restauração acidental de dados do tenant anterior. Repetir o reset é idempotente.

São preservados:

- aplicativo, versão, dependências, serviço/autostart e updater;
- Linux e configuração de Wi-Fi/NetworkManager;
- `.env` de instalação e endpoint mínimo de reconexão;
- `db/display_orientation` e configuração física de tela;
- serial de hardware ou machine-id (identidade da instalação física);
- logs técnicos que não fazem parte do banco operacional.

`FACTORY_RESET_DEVICE`, que poderia apagar Wi-Fi e outras configurações físicas, não foi implementado e nunca é usado no encerramento da Empresa.

## Pagamento em andamento

O backend impede unlink, substituição ou encerramento definitivo enquanto existir tentativa financeira não resolvida. Há uma segunda proteção local: se uma resposta autoritativa de reset chegar com pagamento/order em andamento, o Terminal guarda o reset pendente e aciona a reconciliação já existente. O marcador destrutivo só é escrito quando a sessão deixa de estar não resolvida.

Falha de rede na reconciliação não é tratada como conclusão e não apaga os dados. O reset pendente é retomado pela mudança de estado da sessão e pelas próximas consultas de lifecycle.

O Terminal persiste `generation`, tentativa local, `cartId`, `orderId`, `paymentAttemptId`, transaction ID e último estado na tabela SQLite singleton `active_payment`. Reinício do processo ou do Raspberry restaura `RECONCILIATION_PENDING`; a UI consulta primeiro o backend e só limpa o checkpoint após resultado terminal ou confirmação autoritativa de ausência antes de qualquer POST financeiro.

## Nova ativação e telemetria

Depois do reset, a tela volta ao tema padrão `APP 24/7 — Terminal não configurado`. O equipamento pode ser cadastrado em outra Empresa/Condomínio e recebe um novo `terminalId`; como o SQLite e os cursores antigos foram removidos, o `SyncService` cria o schema e faz full sync normal.

No estado não ativado não é enviada telemetria de tenant: o serviço atual só inicia após `Terminal.is_activated()`. O serial físico continua disponível para ativação/suporte, mas não é atribuído à Empresa encerrada em eventos novos.

## Auditoria

O backend registra `COMPANY_DEACTIVATED`, `TERMINAL_RESET_REQUIRED`, `TERMINAL_FACTORY_RESET_STARTED` e `TERMINAL_FACTORY_RESET_COMPLETED`, além dos eventos de conta e Point descritos no documento de Mercado Pago.
