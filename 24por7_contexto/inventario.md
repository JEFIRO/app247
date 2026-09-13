# Inventário físico

Voltar para [[00-index]]. Estoques e ledger em [[estoque]]; schema em [[database]].

## Escopo e modelo

O inventário registra uma contagem física sem substituir saldo silenciosamente. `inventario` guarda tenant, tipo/ID da localização, estado, modo cego, responsáveis e instantes. `inventario_item` congela produto, saldo `DECIMAL(15,3)`, versão e `updated_at` do estoque no instante da abertura.

A UI administrativa oferece inicialmente o Estoque Central. O domínio e a API também aceitam `CONDOMINIO`; `DEPOSITO` continua reservado e é rejeitado enquanto não existir esse estoque.

Estados do inventário: `EM_CONTAGEM`, `COM_CONFLITO`, `FINALIZADO` e `CANCELADO`. O enum reserva `RASCUNHO`, embora a criação atual já abra a sessão em `EM_CONTAGEM`. Itens usam `PENDENTE`, `OK`, `FALTA`, `SOBRA` ou `CONFLITO`.

## Contagem e modo cego

Cada item aceita uma quantidade com no máximo três casas. A diferença auditável é:

```text
quantidade_contada - saldo_sistema_snapshot
```

Em contagem cega, saldo e diferença não saem no DTO enquanto a sessão está em contagem. Produto, SKU, barcode principal e localização compacta de Planograma continuam visíveis. Ao finalizar, cancelar ou detectar conflito, as referências necessárias para revisão são reveladas.

Produto sem Planograma participa normalmente. Planograma só informa localização; não altera quantidade nem disponibilidade.

## Finalização, concorrência e idempotência

Na finalização, inventário e saldos são bloqueados para escrita. Para cada item ainda não aplicado, a versão atual é comparada com a versão do snapshot:

- versão igual: aplica a contagem;
- versão diferente: preserva o saldo, grava o saldo atual e marca `CONFLITO`;
- diferença zero: mantém o item histórico e não cria movimento;
- diferença diferente de zero: cria `MovimentacaoEstoque` do tipo `AJUSTE_INVENTARIO`.

Exemplo: snapshot `100.000`, contagem `97.000`, saldo ainda `100.000` resulta em delta `-3.000`, anterior `100.000` e posterior `97.000`. A chave `INVENTARIO:{inventarioItemId}` e `ajuste_aplicado` impedem duplicação. Finalizar novamente um inventário concluído só retorna seu estado.

Um conflito não desfaz ajustes já aplicados em outros produtos. O item conflitante deve ser recontado; nessa ação seu snapshot é atualizado sob lock e uma nova finalização processa somente o que ainda está pendente. Inventário com ajuste parcial não pode ser cancelado. Cancelamento anterior a qualquer ajuste não muda saldo.

## Tenant e auditoria

Empresa vem de `EmpresaContext`. Condomínio, produto e estoques são consultados dentro do tenant; FKs compostas protegem os itens. Como `local_id` é polimórfico, o vínculo da localização é validado pelo service.

Eventos no `AuditLog`: `INVENTARIO_CRIADO`, `AJUSTE_INVENTARIO`, `INVENTARIO_FINALIZADO` e `INVENTARIO_CANCELADO`. O ledger referencia `inventario_id` e `inventario_item_id`.

## API

- `POST /inventarios`: abre inventário (`localTipo`, `localId`, `descricao`, `observacao`, `contagemCega`);
- `GET /inventarios`: histórico paginado, com filtros `status` e `localTipo`;
- `GET /inventarios/{id}`: detalhe e progresso;
- `PUT /inventarios/{id}/itens/{itemId}/contagem`: registra/reconta (`quantidadeContada`, `motivoAjuste`);
- `POST /inventarios/{id}/finalizar`;
- `POST /inventarios/{id}/cancelar`.

## Flutter

`InventarioListPage` permite iniciar contagem central, filtrar e continuar histórico. `InventarioDetailPage` oferece busca por nome/SKU/barcode, filtros de status, progresso, edição da contagem, localização, resumo, finalização, cancelamento e revisão de conflitos. Quantidades usam `DecimalValue`, nunca `double`.
