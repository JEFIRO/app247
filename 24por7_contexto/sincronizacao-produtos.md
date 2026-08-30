# Sincronização de produtos por Terminal

Voltar para [[00-index]]. Catálogo físico em [[estoque]], contratos em [[api]] e transporte em [[websocket]].

## Responsabilidades

O WebSocket é notificação/invalidação; o endpoint HTTP é a fonte dos dados. Nenhum produto completo é enviado no evento e não há fila de notificações offline.

```text
mutação de Produto ou disponibilidade
  -> persistência/commit
  -> ProdutoCatalogChangedEvent (AFTER_COMMIT)
  -> condomínios afetados -> Terminais
  -> PRODUCT_SYNC_REQUIRED
  -> GET /produtos/sync
  -> UPSERT/REMOVE no SQLite
```

## Seleção dos condomínios

- Produto criado somente na empresa: nenhum condomínio e nenhum Terminal notificado.
- Associação criada/reativada: apenas o condomínio da associação.
- Associação removida: apenas o condomínio removido; a linha permanece com `ativo=false`.
- Produto global atualizado/ativado/desativado: condomínios que possuem associação ativa para o produto.
- `quantidade > 0` nunca é usada como critério. Saldo `-1` pode permanecer disponível.
- Entradas, ajustes e baixas de venda não são eventos de catálogo.

O evento guarda os IDs dos condomínios ainda dentro da transação, evitando perder o destino de uma desativação. O listener consulta os Terminais e envia somente após commit. Em rollback, o listener não executa.

## Contrato WebSocket

Canal reutilizado: `/payment-socket/{terminalId}`.

```json
{
  "type": "PRODUCT_SYNC_REQUIRED",
  "reason": "PRODUCT_REMOVED_FROM_CONDOMINIUM",
  "productId": "abc123"
}
```

O cliente deve tratar eventos repetidos como idempotentes. Se já houver sync em andamento, deve marcar uma nova execução pendente ou ignorar duplicatas cobertas pelo mesmo ciclo; nunca iniciar sincronizações concorrentes.

## Contrato HTTP

Primeira chamada:

```http
GET /produtos/sync?uuidTerminal={uuid}
```

Retorna `fullSync=true` e todos os produtos atuais do condomínio como `UPSERT`. O cliente deve tratar o resultado como snapshot integral: aplicar os UPSERTs e desativar localmente itens ausentes.

Chamadas seguintes:

```http
GET /produtos/sync?uuidTerminal={uuid}&lastSync=2026-08-24T16:50:32.123Z
```

Retornam alterações em `(lastSync, syncAt]`, considerando `Produto.updateAt` e `EstoqueCondominio.updatedAt`. `syncAt` é `Instant` UTC gerado pelo backend e só deve ser salvo após a transação SQLite concluir.

Operações:

- `UPSERT`: `produto` completo, incluindo dados globais e quantidade corrente da associação;
- `REMOVE`: `productId`, com `produto=null`; desativar ou remover localmente de forma idempotente.

## Recuperação e reconexão

Ao iniciar, reconectar o socket ou recuperar internet, o Terminal deve solicitar sync usando o último `syncAt` confirmado. Perder `PRODUCT_SYNC_REQUIRED` não perde dados, pois as mudanças permanecem detectáveis pelos timestamps persistidos.

O backend envia uma mensagem genérica por evento/Terminal e não implementa debounce distribuído. O desenho permite coalescência no cliente: uma única sincronização lê todas as mudanças até o novo cursor.

## Diagnóstico de cache vazio

O Terminal `6bdaf9dc-4d83-4648-938b-aa0707b4b000` foi validado em 24 de agosto de 2026. Ele pertence ao condomínio `aaeec488-a6c5-425b-9d1e-3c2325e45c79`, da empresa `49a8696b-f7d2-48cb-82c8-d15a37f30341`. A empresa possuía um produto ativo, mas o condomínio possuía zero linhas em `EstoqueCondominio`; por isso o FULL real retornou corretamente `fullSync=true, changes=[]`.

O problema adicional estava no cliente: SQLite vazio ainda usava cursor antigo. O Terminal agora mantém `catalog_sync_state`; marcador ausente ou contagem divergente omite `lastSync` e exige FULL. FULL vazio confirmado inicializa corretamente um catálogo de zero itens, evitando loop de FULL para condomínio legitimamente vazio.

## Estado do Terminal Python

**Adaptado em 24 de agosto de 2026.** O cliente em `../TerminalPython` agora:

1. chama `/produtos/sync` com `uuidTerminal` e omite `lastSync` na primeira execução;
2. valida `{syncAt, fullSync, changes}` e persiste exatamente o `syncAt` do backend somente após o commit SQLite;
3. aplica FULL/INCREMENTAL e todas as operações `UPSERT/REMOVE` em uma única transação;
4. separa `PAYMENT_STATUS` de `PRODUCT_SYNC_REQUIRED` no `PaymentListener`;
5. solicita sync no startup, em eventos, na conexão/reconexão e periodicamente;
6. serializa execuções com lock, `sync_in_progress` e `sync_pending`, coalescendo eventos sem perder o último aviso;
7. preserva cache e cursor em falha HTTP, resposta inválida ou rollback.
8. só usa cursor quando um FULL anterior está marcado e a contagem ativa local é consistente.

A suíte do Terminal cobre contrato, atomicidade, cursor legado, offline, concorrência, reconexão e roteamento de eventos.
