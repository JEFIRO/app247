# Banco de dados

Voltar para [[00-index]]. Visão dos fluxos em [[arquitetura]] e dados de autenticação em [[autenticacao]].

## Bancos configurados

O perfil `prod` configura MySQL em `127.0.0.1:3306/sistema`, driver MySQL e `spring.jpa.hibernate.ddl-auto=update`. O perfil `dev` configura H2 persistido em `./data/meubanco`, console `/h2-console` e também `ddl-auto=update`. O arquivo base não seleciona perfil.

Flyway está no classpath com suporte MySQL. Como Hibernate também está em `update`, o schema executado pode conter alterações que não existem nas migrations.

## Modelo relacional JPA

Todos os IDs de entidade são strings geradas com UUID, salvo as incompatibilidades de repository descritas adiante.

| Entidade / tabela | Relações e finalidade observada |
|---|---|
| `Empresa` / `empresa` | Raiz de tenant com dados cadastrais, `tenantId` único, ativo e data de cadastro. |
| `Condominio` / `condominio` | Pertence obrigatoriamente a empresa e possui endereço 1:1. A cardinalidade é consultada via repository. |
| `Endereco` / `endereco` | Pertence a empresa; é referenciado pelo condomínio. |
| `User` / `users` | Pertence a empresa e opcionalmente a condomínio; implementa `UserDetails`; possui orders. |
| `Terminal` / `terminal` | Pertence obrigatoriamente apenas a condomínio; pode guardar `mercadoPagoTerminalId`, único quando preenchido. |
| `GrupoTributario` / `grupo_tributario` | Pertence a empresa; contém NCM, CEST, CFOP, CST/CSOSN e alíquotas. |
| `Produto` / `produto` | Pertence a empresa e opcionalmente a grupo tributário; representa catálogo, preço e peso, sem saldo. |
| `EstoqueCondominio` / `estoque_condominio` | Liga condomínio e produto, com saldo decimal, ativo, timestamps de catálogo e unicidade do par. |
| `MovimentacaoEstoque` / `movimentacao_estoque` | Auditoria de entrada/ajuste/reserva/venda/liberação, com chave única de idempotência. |
| `Carrinho` / `carrinho` | Pertence a empresa e a terminal; status, subtotal e itens com cascade/orphan removal. |
| `Item` / `item` | Pertence a empresa, carrinho e produto; guarda snapshot comercial, quantidade e pesos. |
| `Order` / `orders` | Pertence a empresa; relaciona carrinho 1:1, usuário opcional e pagamento 1:1. Espelha dados da order Mercado Pago. |
| `Pagamento` / `pagamento` | Pertence a empresa; lado inverso da relação com order; guarda valor, origem, tipo, status e dados da transação. |
| `MercadoPagoConta` / `mercado_pago_conta` | Relação 1:1 com empresa; persiste somente credenciais/metadados OAuth, sem identificação de terminal físico. |
| `WebhookEvent` / `webhook_event` | Modelo de ID de evento e data de recebimento; não é usado pelo fluxo atual. |

## Estados persistidos

Enums JPA são gravados como texto. Entre os principais:

- carrinho: `OPEN`, `WAITING_WEIGHT`, `READY_FOR_PAYMENT`, `PAYMENT_PENDING`, `PAID`, `CANCELED`;
- item: `WAITING_WEIGHT`, `VALIDATED`, `INVALID_WEIGHT`, `REMOVED`;
- terminal: `ONLINE`, `OFFLINE`, `MANUTENCAO`;
- origem de order: `TERMINAL`, `APP`;
- order: `PENDING`, `CREATED`, `AT_TERMINAL`, `PROCESSED`, `CANCELED`, `EXPIRED`, `FAILED`, `REFUNDED`, `ACTION_REQUIRED`;
- pagamento: `PENDING`, `PROCESSED`, `CANCELED`, `EXPIRED`, `FAILED`, `REFUNDED`, `ACTION_REQUIRED`;
- origem do pagamento: `TERMINAL`, `CHECKOUT`.

## Repositories

- CRUD JPA simples: carrinho, empresa, endereço, pagamento e webhook event.
- `CondominioRepository`: ID `String`, lista por empresa e busca por condomínio + empresa.
- `TerminalRepository`: ID `String`, lista por condomínio + empresa e busca por terminal + `condominio.empresa`.
- `ProdutoRepository`: possui consultas tenant-aware por empresa; o sync físico fica nas queries de associação de `EstoqueCondominioRepository`.
- `UserRepository`: busca por e-mail/CPF, existência por CPF e projeção paginada de orders.
- `OrderRepository`: projeção paginada de orders por usuário.
- `OauthMercadoPagoRepository`: busca conta por `mpUserId` ou `empresa.id`; `empresa_id` é único.
- `PagamentoRepository`: busca por `transactionId`.
- `EstoqueCondominioRepository`: consultas por condomínio/empresa, soma agregada e lock pessimista.
- `MovimentacaoEstoqueRepository`: chave idempotente e listagem tenant-aware.
- `CheckoutSessionRepositoryImpl`: não é JPA; usa Redis com chave `session:{id}` e TTL de 15 minutos.

Condomínio, terminal, produto e estoque possuem consultas tenant-aware nos fluxos administrativos. Usuário, carrinho, Order e pagamento não recebem filtro automático de tenant.

## Histórico Flyway

| Versão | Mudança declarada |
|---|---|
| V1 | cria `empresa` |
| V2 | cria `produto` e FK para empresa |
| V3 | cria `carrinho` e FK para empresa |
| V4 | cria `item`, FK para empresa e carrinho |
| V5 | cria `users` e FK para empresa |
| V6 | cria `orders`, FKs para empresa, carrinho e usuário |
| V7 | cria `pagamento` e FK para empresa |
| V8 | cria `webhook_event` com FK de empresa |
| V9 | cria `condominio` e FK para empresa |
| V10 | adiciona FK usuário-condomínio |
| V11 | cria `terminal`, FKs para empresa e condomínio |
| V12 | cria `endereco` e FK para empresa |
| V13 | adiciona FK condomínio-endereço |
| V14 | cria `grupo_tributario` |
| V15 | adiciona FK produto-grupo tributário |
| V16 | cria `mercado_pago_conta` |
| V17 | adiciona FK de `orders.id_pagamento` para `pagamento` |
| V18 | amplia o UUID do terminal, renomeia a FK para `condominio_id` e remove `empresa_id` redundante |
| V19 | move o ID Point para `terminal.mercado_pago_terminal_id`, remove `mercado_pago_conta.terminal_id` e cria unicidades |
| V20 | cria estoque e movimentações por condomínio, migra saldo legado inequívoco, adiciona FKs de item/terminal, torna Order única por carrinho e remove estoque de `produto` |

## Divergências verificadas entre JPA e migrations

Estas divergências descrevem o repositório atual; não indicam que uma correção foi aplicada:

- `Order.originRequest` é persistido pela entidade, mas `orders` não possui a coluna correspondente nas migrations.
- A entidade `Pagamento.transactionId` mapeia por convenção para `transaction_id`, enquanto V7 cria `id_transaction`.
- `WebhookEvent` não possui campo `empresa`, mas V8 exige `empresa_id NOT NULL`.
- `Terminal.idTerminal` é UUID string; V18 amplia `id_terminal` de 26 para 36 caracteres.
- `GrupoTributario.id_tributacao` é UUID, mas V14 limita a coluna a 25 caracteres. A entidade escreve `aliquotaConfins`, cujo nome por convenção é `aliquota_confins`, enquanto a migration cria `aliquota_cofins`.
- Até V19, `Produto.codigo` era único na tabela inteira; V20 substitui essa regra por `(empresa_id, codigo)`.
- `User.email` e `User.cpf` são únicos globalmente na migration; o JPA não declara explicitamente esses `unique`, embora as consultas assumam CPF único.
- `EnderecoRepository` ainda declara ID `Long`, enquanto a entidade usa `String`. Condomínio, terminal e heartbeat foram alinhados para `String`.
- V11 declara `id_endereco BIGINT` no terminal, mas a entidade `Terminal` não possui relação/campo de endereço.
- V4 cria `created_at` e `updated_at` em `item`, mas a entidade não os mapeia.
- A migration V7 não cria `installments`, embora a entidade `Pagamento` possua o campo.
- O relacionamento `Order`–`Pagamento` só ganha FK em V17; `Pagamento.order` é o lado inverso e não possui `id_order`.

Com `ddl-auto=update`, parte dessas diferenças pode ser alterada automaticamente por Hibernate em um banco existente. O resultado real deve ser conferido no schema implantado e na tabela de histórico do Flyway.

## Diagrama relacional atual

```text
empresa
├── users
├── condominio
│   ├── terminal
│   └── estoque_condominio
│       ├── produto
│       └── movimentacao_estoque
├── produto
├── carrinho
│   ├── terminal
│   └── item
│       └── produto
├── orders
│   ├── carrinho UNIQUE
│   ├── users opcional
│   └── pagamento opcional
└── mercado_pago_conta UNIQUE por empresa
```

## Chaves e constraints críticas

- PKs: UUID string, normalmente `VARCHAR(36)`.
- `condominio.empresa_id`, `terminal.condominio_id`, `produto.empresa_id`, `estoque_condominio` e relações comerciais possuem FKs declaradas nas migrations atuais.
- `UNIQUE(estoque_condominio.condominio_id, produto_id)`.
- `UNIQUE(movimentacao_estoque.chave_idempotencia)`.
- `UNIQUE(orders.id_carrinho)` a partir de V20.
- `UNIQUE(produto.empresa_id, codigo)` a partir de V20.
- `UNIQUE(mercado_pago_conta.empresa_id)` e `UNIQUE(terminal.mercado_pago_terminal_id)`.
- Quantidade de estoque não possui `CHECK` que impeça negativos.

## Risco de histórico Flyway

No worktree auditado, V6 e V7 estão modificadas em relação ao Git, antigas V17/V18 aparecem removidas e os números foram reutilizados por outros arquivos. Bancos que já aplicaram os checksums anteriores podem falhar na validação Flyway. Isso deve ser resolvido antes de produção; detalhes em [[auditoria-bugs]].

O profile de testes não valida essas migrations: usa H2, `create-drop` e `spring.flyway.enabled=false`.

## Hierarquia operacional após V18

```text
empresa.id
   ^
   | condominio.empresa_id (NOT NULL, FK)
condominio.id_condominio
   ^
   | terminal.condominio_id (NOT NULL, FK)
terminal.id_terminal
```

Não existe mais `terminal.empresa_id`. A migration remove as FKs antigas, renomeia a coluna do condomínio, elimina a coluna redundante e recria `fk_terminal_condominio`. Não há constraint única nessas FKs: uma empresa pode possuir vários condomínios e cada condomínio vários terminais.

## Mercado Pago após V19

```text
empresa.id
   |
   +--- mercado_pago_conta.empresa_id UNIQUE
   |       access_token
   |       refresh_token
   |       mp_user_id
   |       expiração/scope
   |
   +--- condominio -> terminal
                         mercado_pago_terminal_id UNIQUE NULL
```

`mercado_pago_conta.terminal_id` é removido. Para preservar dados sem atribuição arbitrária, V19 migra o valor legado somente quando a empresa possui exatamente uma conta OAuth com terminal preenchido e exatamente um terminal interno. Nos demais casos o vínculo precisa ser refeito pela API administrativa. A criação de `UNIQUE(empresa_id)` falha se já houver contas duplicadas; a migration não apaga credenciais silenciosamente.

## Redis como armazenamento complementar

Redis não substitui o banco relacional. Ele armazena:

- `session:{uuid}`: `CheckoutSession`, TTL 15 minutos;
- `oauth:mp:{state}`: ID do usuário, TTL 10 minutos;
- `recovery:{cpf}`: código/DTO de recuperação, TTL 15 minutos;
- `reset:{cpf}`: token de redefinição, TTL 15 minutos;
- `email_validation_queue:{email}`: código de validação, TTL 15 minutos;
- listas `mp_queue`, `recovery_queue` e `email_validation_queue`.
- filas intermediárias `*:processing`, DLQs `*:dlq` e contadores `*:retry:*` com TTL de 24 horas;
- `mp_webhook:{action}:{orderId}:{version}`: deduplicação de webhook, TTL 24 horas.

O `RedisTemplate<String,Object>` usa chaves string e serialização JSON para valores e hashes. Alguns pontos injetam esse bean com tipo genérico `<String,String>`; em runtime é o mesmo bean configurado.

## Migrations V21 a V24

- **V21** adiciona `orders.mp_event_version`, `orders.mp_event_date` e índice para `mp_order_id`;
- **V22** adiciona `item.unidade_medida`, completando o snapshot comercial;
- **V23** alinha `origin_request`, `transaction_id`, `installments`, comprimentos de IDs e colunas de catálogo/tributação com os mappings JPA.
- **V24** adiciona `created_at/updated_at` a `estoque_condominio`, eleva timestamps do produto para precisão de microssegundos e cria índices para sync incremental.

O profile `prod` usa `spring.jpa.hibernate.ddl-auto=validate`; o Hibernate não deve mais corrigir silenciosamente o schema produtivo. Isso exige aplicar e validar as migrations previamente. A aplicação dessas versões em MySQL não foi executada nesta tarefa porque não há banco de homologação fornecido.
