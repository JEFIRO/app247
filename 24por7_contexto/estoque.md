# Estoque por condomínio

Voltar para [[00-index]]. Relações físicas em [[banco-de-dados]] e estados externos em [[mercado-pago]].

## Modelo implementado

```text
Empresa
├── Produto (catálogo)
└── Condominio
    ├── Terminal
    └── EstoqueCondominio
        └── Produto
```

`Produto` pertence a uma empresa e não guarda quantidade. `EstoqueCondominio` associa um produto a um condomínio, contém quantidade decimal, `ativo`, `createdAt` e `updatedAt`, e possui unicidade `(condominio_id, produto_id)`. Produto e condomínio precisam pertencer à mesma empresa.

Disponibilidade não depende de saldo positivo: inclusive `quantidade=-1` com `ativo=true` continua disponível. A disponibilidade de catálogo exige somente associação ativa e produto global ativo.

Para o Terminal, a seleção é sempre `Terminal -> Condomínio -> EstoqueCondominio -> Produto`. Produto existente apenas na Empresa não aparece sem associação no condomínio. Consequentemente, FULL SYNC vazio é estado válido quando não há vínculos, e não deve ser confundido com falha HTTP.

O saldo geral da empresa não é persistido separadamente. `EstoqueCondominioRepository` calcula `SUM(quantidade) GROUP BY produto` no banco.

## Carrinho e snapshot

O carrinho possui relação obrigatória com `Terminal`. Sua empresa é derivada e validada pela cadeia `Terminal -> Condominio -> Empresa`. Todos os produtos enviados precisam pertencer a essa empresa e produtos duplicados na mesma criação são rejeitados.

`Item` possui FK para `Produto`, FK para `Carrinho`, quantidade e snapshots de código, nome, foto, preço unitário e unidade de medida. Assim mudanças posteriores no catálogo não alteram os dados básicos ou subtotal já armazenados. `Carrinho.addItem/removeItem` mantém os dois lados da associação. Uma constraint garante no máximo uma Order por carrinho, e o service rejeita checkout de carrinho que não esteja `OPEN`.

Na fronteira do pagamento, o backend recalcula `sum(unitPrice * quantity)` a partir dos snapshots persistidos. Carrinho vazio, item sem produto/preço/quantidade, subtotal divergente ou associação de empresa inconsistente é rejeitado antes da Order, reserva e cobrança Point. Essa checagem complementa Bean Validation do request e protege chamadas internas que não passam pelo controller.

Quando `EmpresaContext` existe, criação e consulta de carrinho validam a empresa derivada de `Terminal -> Condominio -> Empresa`. Sem contexto, necessário aos fluxos de terminal ainda abertos durante desenvolvimento, nenhuma empresa é inventada: ela é derivada do terminal persistido.

## Ciclo de movimentação

```text
Carrinho aberto
   -> criação da Order
   -> RESERVA (reduz saldo)
   -> pagamento processado
      -> VENDA (confirma; delta zero)
   -> cancelado/falhou/expirou
      -> LIBERACAO_RESERVA ou CANCELAMENTO (recompõe saldo)
```

A reserva permite saldo negativo. Cada saldo negativo é persistido normalmente e gera log `WARN`; não há exceção de estoque insuficiente.

Uma venda aprovada não desconta pela segunda vez. Uma Order sem reserva não recebe devolução. Reembolso/cancelamento libera a quantidade reservada uma única vez.

## Idempotência e concorrência

Cada efeito por item usa chave única:

- `{order}:{item}:RESERVA`;
- `{order}:{item}:VENDA`;
- `{order}:{item}:LIBERACAO`.

`movimentacao_estoque.chave_idempotencia` possui `UNIQUE`, protegendo contra reentregas e concorrência além da deduplicação temporária do Redis. O saldo é carregado com `PESSIMISTIC_WRITE` dentro de transação, serializando vendas simultâneas do mesmo produto no mesmo condomínio.

A movimentação de quantidade (`ENTRADA`, `AJUSTE`, `RESERVA`, `VENDA`, liberação ou cancelamento) não altera `EstoqueCondominio.updatedAt` e não dispara sync de catálogo. Quantidade tem ciclo próprio e não deve causar uma invalidação para cada venda.

Alterações de disponibilidade usam `EstoqueCondominio.alterarDisponibilidade`, atualizam `updatedAt` em UTC e publicam `ProdutoCatalogChangedEvent`. A entrega ao Terminal ocorre somente em `AFTER_COMMIT`; rollback não envia mensagem. Terminal desconectado recupera as mudanças persistidas por `GET /produtos/sync`.

## APIs administrativas

- `GET /condominios/{condominioId}/estoque` — estoque do condomínio autenticado;
- `POST /condominios/{condominioId}/estoque` — disponibiliza produto e registra entrada inicial;
- `POST /condominios/{condominioId}/estoque/{produtoId}/entrada` — acrescenta quantidade;
- `PUT /condominios/{condominioId}/estoque/{produtoId}` — ajuste para saldo absoluto;
- `DELETE /condominios/{condominioId}/estoque/{produtoId}` — soft delete da disponibilidade e tombstone para sync;
- `GET /condominios/{condominioId}/estoque/movimentacoes` — auditoria cronológica do condomínio;
- `GET /estoque/geral` — soma por produto na empresa autenticada.

As rotas estão públicas temporariamente na camada HTTP. IDs de empresa não são aceitos no body; as operações continuam dependendo do tenant presente em `EmpresaContext`.

## Migração V20

V20 cria `estoque_condominio` e `movimentacao_estoque`, adiciona as FKs `item.id_produto` e `carrinho.id_terminal`, torna `orders.id_carrinho` único e remove `produto.quantidade`.

O saldo antigo só é migrado automaticamente quando a empresa possui exatamente um condomínio. Para empresas com mais de um condomínio não há destino comprovável, portanto a migration não duplica nem distribui o valor legado.

O campo `quantidade` permanece temporariamente opcional em `CreateProductDTO` apenas para desserializar clientes antigos; ele está depreciado e não altera estoque. Estoque inicial deve ser cadastrado pela API do condomínio.

## Migração V24

V24 acrescenta `created_at` e `updated_at` com microssegundos a `estoque_condominio`, aumenta a precisão dos timestamps de `produto` para microssegundos e cria índices para as janelas incrementais. A remoção lógica mantém a linha e permite que o Terminal receba `REMOVE`; exclusão física direta não faz parte do caso de uso suportado porque eliminaria o tombstone.
