# Estoques, transferências e planograma

Voltar para [[00-index]]. Schema em [[database]], sync em [[sincronizacao-produtos]] e auditoria em [[audit-log]].

## Regra de domínio

```text
Produto
├── EstoqueEmpresa
│   └── Planograma (organização opcional)
│   └── Inventario (contagem física)
└── EstoqueCondominio

EstoqueEmpresa
    │ TransferenciaEstoque
    ▼
EstoqueCondominio
```

- `Produto` é catálogo e não possui saldo.
- `EstoqueEmpresa` é a quantidade fisicamente presente no estoque central da Empresa.
- `EstoqueCondominio` é a quantidade fisicamente presente no mercado daquele Condomínio.
- `TransferenciaEstoque` é a operação logística controlada entre localizações.
- `MovimentacaoEstoque` é o histórico imutável de todo efeito sobre saldo.
- `Planograma` é a localização/organização física opcional no central; capacidade de posição nunca é saldo.
- `Inventario` congela o saldo e transforma divergências confirmadas em movimentos auditáveis.

O estoque total sob gestão pode ser calculado como central mais condomínios, mas não substitui nem mistura os saldos físicos.

## Estoque central

`estoque_empresa` possui uma linha por `(empresa_id, produto_id)`, quantidade `DECIMAL(15,3)`, ativo e versão otimista. A primeira operação pode criar a linha com saldo zero. Assim, Produto pode existir sem estoque central e a UI oferece o catálogo para iniciar uma entrada.

Entrada, saída e ajuste absoluto sempre geram movimento assinado com saldo anterior/posterior e AuditLog. A política existente permanece: saldo negativo é válido e não é usado como disponibilidade.

Consultas administrativas são paginadas e aceitam nome, SKU, barcode, categoria, ativo e ordenação. Alterações somente no central não disparam Product Sync.

## Estoque por condomínio e checkout

`EstoqueCondominio` continua sendo a fonte da disponibilidade do Terminal. `ativo=true` disponibiliza; `quantidade > 0` não é requisito. A unicidade é `(condominio_id, produto_id)` e FKs compostas garantem Produto e Condomínio da mesma Empresa.

O fluxo de venda permanece: criação da Order gera `RESERVA`, aprovação gera `VENDA` com delta zero, falha/cancelamento gera liberação idempotente. Quantidades são fracionadas e negativas continuam permitidas.

## Ledger único

`movimentacao_estoque` foi generalizada na V9 e recebeu referências de inventário na V10. Cada registro aponta exatamente para uma localização por meio do `CHECK ck_movimento_local_unico`:

- `estoque_empresa_id`; ou
- `estoque_condominio_id`.

O campo `quantidade` é delta assinado: entradas positivas, saídas negativas e venda confirmada zero. Order/OrderItem continuam opcionais para movimentos comerciais; `transferencia_id` correlaciona os lados logísticos e `created_by` identifica o responsável quando a operação veio de uma sessão administrativa. Chaves de idempotência são únicas por Empresa.

## Transferências

A primeira versão implementa somente `ESTOQUE_EMPRESA → CONDOMINIO` e usa estados `RASCUNHO`, `CONCLUIDA` e `CANCELADA`. Estados de trânsito não foram criados porque ainda não existe workflow operacional que os sustente.

Confirmação:

1. bloqueia a transferência;
2. valida Empresa, direção, Condomínio e produtos;
3. bloqueia os saldos em ordem determinística de Produto;
4. debita o central;
5. cria ou incrementa o estoque do condomínio;
6. grava `SAIDA_TRANSFERENCIA` e `ENTRADA_TRANSFERENCIA`;
7. marca a transferência concluída e audita;
8. publica o evento de catálogo, entregue ao Terminal somente em `AFTER_COMMIT`.

Tudo ocorre em uma única transação. Qualquer falha no destino reverte débito, crédito, movimentos e status. Confirmar novamente uma transferência concluída não repete os efeitos. Condomínio sem associação recebe `EstoqueCondominio ativo=true`, decisão necessária para que o produto transferido se torne disponível; associação inativa também é reativada.

Cancelar um rascunho não altera saldos. Transferência concluída não é apagada nem reescrita; um futuro estorno deverá gerar movimentos compensatórios explícitos.

Os enums de localização reservam `DEPOSITO`. A evolução para múltiplos depósitos deve introduzir `deposito` e `estoque_deposito`, preservando os tipos e o ledger comum; não há depósito adicional nesta versão.

## Product Sync

Uma confirmação publica `ProdutoCatalogChangedEvent` com motivo `STOCK_TRANSFER_COMPLETED` e somente o Condomínio de destino. O listener atual resolve seus Terminais e envia `PRODUCT_SYNC_REQUIRED` após commit. O sync incremental lê `EstoqueCondominio.updatedAt`; o central nunca é enviado ao Terminal.

## Planograma

`Planograma` pertence à Empresa, `PlanogramaPosicao` descreve setor/corredor/estante/módulo/prateleira/posição e `PlanogramaProduto` guarda facings, capacidade e níveis ideal/mínimo. Coordenadas `x`, `y`, `largura` e `altura` são opcionais para editor visual futuro.

Produto pode ficar sem posição, mudar de posição ou sair do planograma sem alterar Produto, estoque ou disponibilidade. Futuras evoluções possíveis: editor drag-and-drop, mapa de estoque, rota de separação, reposição, ocupação, inventário por corredor e planogramas por depósito.

## Auditoria

São registrados no AuditLog existente: `ESTOQUE_EMPRESA_ENTRADA`, `ESTOQUE_EMPRESA_SAIDA`, `ESTOQUE_EMPRESA_AJUSTE`, `TRANSFERENCIA_CRIADA`, `TRANSFERENCIA_CONFIRMADA`, `TRANSFERENCIA_CANCELADA`, `PLANOGRAMA_CRIADO`, `PLANOGRAMA_ALTERADO`, `INVENTARIO_CRIADO`, `AJUSTE_INVENTARIO`, `INVENTARIO_FINALIZADO` e `INVENTARIO_CANCELADO`.

Inventário físico e a carga inicial por planilha são detalhados em [[inventario]] e [[importacao-produtos]]. Ambos reutilizam o mesmo ledger; nenhum deles altera saldo silenciosamente.

## Verificação

`CentralStockTransferIntegrationTest` cobre entradas, ajuste, delta, transferência, criação ativa do destino, rollback do crédito, idempotência, cancelamento, Condomínio/Produto cross-tenant, ausência de sync para o central, sync restrito ao destino e planograma sem efeito sobre saldo. `InventoryAndProductImportIntegrationTest` cobre contagem, conflitos, importação, múltiplos barcodes, estoque inicial e isolamento. `DatabaseBaselineMySqlTest` valida a V10, as 35 tabelas e FKs cross-tenant no MySQL real quando Docker está disponível.
