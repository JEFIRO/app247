# Banco de dados — arquitetura alvo

## V12 — lifecycle Mercado Pago e Terminal

`V12__mercado_pago_and_terminal_lifecycle.sql` transforma `mercado_pago_conta`
em histórico de vínculos, cria a projeção exclusiva
`mercado_pago_conta_ativa`, mantém o histórico Point em
`terminal_point_binding` e acrescenta lifecycle/reset durável ao Terminal e
`closed_at` à Empresa. A PK `mp_user_id` da projeção ativa é a proteção física
contra duas Empresas adquirirem simultaneamente a mesma conta externa.

## V11 — branding dinâmico da Empresa

`V11__company_branding.sql` adiciona a `empresa`: `nome_exibicao`, `logo_url`,
`logo_dark_url`, `cor_principal`, `cor_secundaria` e `cor_destaque`. As três
cores são não nulas e possuem defaults App 24/7; logos e nome customizado são
opcionais. O tenant continua sendo a própria linha de Empresa, sem tabela ou
credencial paralela.

Este documento descreve o schema reconstruído do App 24/7. A cadeia V1–V26 anterior é referência histórica em [[database-atual]]; ela não é compatível nem reaproveitável nesta baseline de desenvolvimento.

## Princípios

- MySQL e Flyway são a fonte do schema; Hibernate usa `ddl-auto=validate`.
- PKs técnicas são UUID textuais em `CHAR(36)` e se chamam `id`.
- nomes SQL usam `snake_case`; Java usa `camelCase`.
- valores monetários internos usam `DECIMAL(15,6)`/`BigDecimal`.
- quantidades comerciais e pesos usam `DECIMAL(15,3)`/`BigDecimal`.
- instantes absolutos usam `DATETIME(6)` em UTC/`Instant`.
- relações carregam `LAZY` por padrão e não há cascade remove sobre histórico.
- cadastro é desativado (`ativo = false`); dados financeiros e de estoque não têm hard delete operacional.

## Fronteira multi-tenant

`empresa` é a raiz. `condominio` possui `empresa_id`; `terminal` possui apenas `condominio_id`, portanto a fonte da empresa do Terminal continua sendo `Terminal → Condominio → Empresa`.

Tabelas que repetem `empresa_id` o fazem como redundância controlada. Pais expõem `UNIQUE (id, empresa_id)` e filhos usam FKs compostas. `orders` e `carrinho` também congelam `condominio_id` e `terminal_id`; a coerência é garantida por FKs compostas, não pela aplicação apenas.

## Cadeia Flyway nova

| Migration | Responsabilidade |
|---|---|
| `V1__core_tenant.sql` | empresa, endereço, condomínio, usuário e terminal |
| `V2__catalog_and_fiscal.sql` | produto, códigos de barras, classificação fiscal e perfil tributário |
| `V3__stock.sql` | disponibilidade e saldo por condomínio |
| `V4__promotions.sql` | promoções e associação explícita de produtos |
| `V5__carts_sales_and_stock_ledger.sql` | carrinho mutável, venda imutável e ledger de estoque |
| `V6__payments_and_mercado_pago.sql` | tentativas, eventos, OAuth e webhooks |
| `V7__terminal_telemetry.sql` | estado atual, série histórica e alertas |
| `V8__audit.sql` | auditoria administrativa imutável |
| `V9__central_stock_transfers_and_planogram.sql` | estoque central, transferências, planograma e generalização do ledger |
| `V10__inventory_and_product_import.sql` | inventário físico, jobs de importação e referências no ledger |
| `V11__company_branding.sql` | branding e tema dinâmico da Empresa |
| `V12__mercado_pago_and_terminal_lifecycle.sql` | histórico/exclusividade ativa MP, Point e reset durável de Terminal |

## Tabelas finais

### `empresa`

Raiz do tenant. Guarda cadastro comercial e contato, `tenant_id` global, `ativo`, `created_at` e `updated_at`. `cnpj`, `email` e `tenant_id` são únicos.

### `endereco`

Endereço pertencente à empresa. `Condominio.endereco` não usa cascade: o service persiste explicitamente o endereço antes de gravar `condominio.endereco_id`, evitando entidade transient e remoções acidentais por cascata.

### `condominio`

Unidade operacional da empresa. Possui endereço exclusivo opcional, CNPJ opcional, estado ativo e timestamps. Expõe as chaves únicas `(id, empresa_id)` para composição multi-tenant. Na edição, o endereço existente é atualizado no lugar, sem criar registros órfãos.

### `users`

Identidade autenticável. Pertence obrigatoriamente à empresa e opcionalmente ao condomínio da mesma empresa. CPF e e-mail permanecem globais por serem usados na autenticação sem seletor de tenant.

### `terminal`

Vínculo operacional do equipamento com um Condomínio. `codigo` é um nome único no condomínio; `serial_number` identifica a ativação e é liberado quando o reset é confirmado. `lifecycle_state` conserva `ACTIVE`, `RESET_REQUIRED` ou `UNACTIVATED` e os timestamps tornam o reset durável para equipamentos offline. `mercado_pago_terminal_id` é somente a projeção Point ativa. Não existe `terminal.empresa_id`; a Empresa vem de `Terminal → Condomínio → Empresa`.

### `perfil_tributario`

Ponto de extensão para regras tributárias por empresa. Não contém CFOP ou alíquotas rígidas nesta fase; esses valores dependem da operação fiscal futura.

### `produto`

Cadastro comercial da empresa: `codigo_interno`, nome, descrição, `preco_venda`, peso, tolerância, categoria, unidade, foto e ativo. Não guarda saldo e não guarda NCM.

Constraints centrais:

- `UNIQUE (empresa_id, codigo_interno)`;
- `UNIQUE (id, empresa_id)` para FKs compostas;
- dinheiro `DECIMAL(15,6)`;
- peso/tolerância `DECIMAL(15,3)`.

### `produto_codigo_barras`

Um produto pode ter vários códigos. Cada registro possui tipo, indicador principal, ativo e timestamp. A regra obrigatória é `UNIQUE (empresa_id, codigo_barras)`: o mesmo EAN pode existir em empresas diferentes, nunca em dois produtos da mesma empresa. Uma coluna gerada e única limita a no máximo um código principal por produto; o service exige exatamente um quando a lista não está vazia.

### `produto_fiscal`

Classificação fiscal 1:1 opcional do produto: NCM, CEST, origem da mercadoria, unidade tributável, fator de conversão, GTIN tributável e perfil tributário opcional. A relação é composta com empresa. CFOP, CST/CSOSN, alíquotas e classificações IBS/CBS não são gravados como verdade fixa do produto.

### `estoque_condominio`

Fonte única de disponibilidade e saldo. Possui empresa, condomínio e produto coerentes por FKs compostas, `quantidade DECIMAL(15,3)`, ativo e versão otimista. `UNIQUE (condominio_id, produto_id)`. Quantidade negativa é válida; disponibilidade depende de `ativo`.

### `estoque_empresa`

Saldo físico do estoque central da Empresa, distinto da soma dos condomínios. Possui `UNIQUE (empresa_id, produto_id)`, quantidade `DECIMAL(15,3)`, ativo e versão otimista. A FK composta `(produto_id, empresa_id)` impede produto cross-tenant. Produto sem linha ainda pode existir no catálogo; a primeira entrada, saída, ajuste ou transferência cria o saldo inicial zero.

### Inventário físico

`inventario` representa uma sessão por tenant/local e `inventario_item` preserva snapshot do saldo e da versão. O item referencia exatamente um estoque central ou de condomínio. Ajustes ficam ligados ao ledger por `movimentacao_estoque.inventario_id` e `inventario_item_id`; conflito de versão impede sobrescrita de movimento concorrente. Veja [[inventario]].

### Importação de produtos

`importacao_produto` guarda arquivo identificado por hash, modo, status e totais. `importacao_produto_item` é o preview/auditoria por aba e linha, com payload normalizado somente para a linha de Produto. O arquivo bruto não é armazenado. Produto, barcodes, fiscal e estoque inicial são persistidos em transação por produto. Veja [[importacao-produtos]].

### Planograma

`planograma` pertence à Empresa; `planograma_posicao` descreve setor, corredor, estante, módulo, prateleira e posição, reservando coordenadas opcionais para um editor visual futuro; `planograma_produto` é a associação explícita com facings, capacidade e níveis ideal/mínimo. Todas as relações de produto usam tenant composto. Nenhuma dessas tabelas altera ou calcula saldo.

### Transferência de estoque

`transferencia_estoque` conserva origem/destino tipados, estado, responsável e timestamps. `transferencia_estoque_item` mantém os produtos e quantidades. A primeira versão executa `ESTOQUE_EMPRESA → CONDOMINIO`; os tipos permitem adicionar `DEPOSITO` e novas direções sem reutilizar Order. Confirmação e cancelamento são históricos, sem DELETE operacional.

### `promocao` e `promocao_produto`

Promoção pertence à empresa. O `CHECK` exige condomínio nulo para `EMPRESA` e preenchido para `CONDOMINIO`. Associação produto/promoção é entidade explícita e tenant-aware. Valores usam seis casas e datas são instantes UTC.

### `carrinho` e `cart_item`

O carrinho é mutável. Ele congela empresa, condomínio e terminal coerentes por FKs. Itens usam quantidade fracionada, preço preciso e dados atuais suficientes para reprecificação; podem ser removidos enquanto o carrinho não virou venda.

### `orders` e `order_item`

`orders` representa a venda/intenção de checkout, não uma tentativa. Um carrinho origina no máximo uma Order. Guarda subtotal, desconto, total calculado e total cobrado separadamente.

`order_item` é um snapshot imutável e independente do `cart_item`: produto, SKU, código de barras usado, nome, unidade, quantidade, preço original/aplicado, promoção, desconto e subtotal. `fiscal_snapshot_version` e `fiscal_snapshot` ficam opcionais para a futura NFC-e; não existe emissor fiscal nesta fase.

### `movimentacao_estoque`

Ledger imutável comum aos dois estoques. O `CHECK ck_movimento_local_unico` exige exatamente um entre `estoque_empresa_id` e `estoque_condominio_id`. Guarda delta assinado, saldo anterior/posterior, Order/OrderItem/transferência e responsável (`created_by`) opcionais, motivo e chave de idempotência. Uma transferência concluída produz `SAIDA_TRANSFERENCIA` e `ENTRADA_TRANSFERENCIA` com a mesma `transferencia_id`; operações administrativas e transferências preservam o usuário autenticado quando disponível. Permite saldos negativos e conserva histórico por no mínimo cinco anos.

### `payment_attempt`

Uma Order possui zero ou várias tentativas. Cada tentativa possui número crescente, provider, channel, método opcional, status, valores solicitado/aprovado, idempotência, referência externa, IDs remotos e timestamps. Retry é sempre novo registro.

Constraints:

- `UNIQUE (order_id, attempt_number)`;
- `UNIQUE (provider, idempotency_key)`;
- `UNIQUE (provider, external_reference)`;
- `UNIQUE (provider, provider_order_id)` quando o ID não é nulo;
- `UNIQUE (provider, provider_payment_id)` quando o ID não é nulo.

### `payment_event`

Histórico append-only das transições de uma tentativa. Guarda evento/versionamento remoto, estados anterior/novo, ocorrência/recebimento e metadata JSON segura.

### `mercado_pago_conta`

Histórico de autorização entre Empresa e identidade externa `mp_user_id`, com status `ACTIVE`, `UNLINKED`, `REVOKED` ou `ERROR` e datas de vínculo/desvínculo. Tokens pertencem somente ao vínculo ativo e são limpos no unlink. `mercado_pago_conta_ativa` é a projeção/lease sem credenciais: sua PK global `mp_user_id`, mais uniques por Empresa e vínculo, garante no banco no máximo um vínculo ativo de cada lado e permite reutilização após remover o lease.

### `terminal_point_binding`

Histórico operacional de cada Point usada por um Terminal e pelo vínculo MP que a autorizou. Somente registros `ACTIVE` podem alimentar a projeção `terminal.mercado_pago_terminal_id`; unlink/substituição encerram o registro sem apagar histórico financeiro.

### `webhook_event`

Inbox/deduplicação/auditoria do webhook: provider, ID externo determinístico, ação, versão, estado `RECEIVED/QUEUED/PROCESSED/DLQ`, timestamps, tentativa resolvida e payload sanitizado. O registro ocorre antes do enqueue Redis; o worker vincula tenant/tentativa e encerra o estado. Não substitui `payment_event`.

### Telemetria

- `terminal_telemetry_current`: uma linha por Terminal;
- `terminal_telemetry_history`: amostras detalhadas indexadas por Terminal/data;
- `terminal_telemetry_alert`: alerta abre uma vez, permanece ativo e depois é resolvido.

Presença online continua baseada em heartbeat/Redis, não em telemetria.

### `audit_log`

Registro append-only de ações administrativas e excepcionais, com ator, entidade, correlação e JSON antes/depois já sanitizado. Não recebe tokens, senhas, JWT nem dados sensíveis de pagamento.

## Índices orientados às consultas

- condomínio por empresa/ativo;
- produto por empresa/ativo, SKU e barcode;
- estoque por condomínio/ativo e produto;
- estoque central por empresa/ativo/produto e busca pelo catálogo;
- transferência por empresa/status/data e destino;
- planograma por empresa/ativo, posição/ordem e localização de produto;
- terminal por condomínio/ativo, serial e Point;
- carrinho por terminal/status/data;
- Order por empresa/data, terminal/status/data e usuário/data;
- tentativa por Order/status, provider/status/data e IDs remotos;
- promoção por empresa/condomínio/período e produto;
- telemetria por Terminal/data e alertas ativos;
- audit log por empresa/data, entidade e correlation ID.

## Exclusões deliberadas

- não há emissão, certificado, XML, CSC, QR Code ou contingência NFC-e;
- não há regra de `quantidade >= 0` no estoque;
- não há FK `orders → payment_attempt` nem reutilização de tentativa encerrada;
- não há cascade remove em venda, pagamento, evento ou ledger;
- não há `ddl-auto=update`.

## Reset do ambiente de desenvolvimento

Esta baseline é destrutiva e não é compatível por checksum ou estrutura com V1–V26. Um ambiente antigo deve ter o banco recriado antes de subir a aplicação. Não se deve apagar apenas `flyway_schema_history` mantendo as tabelas antigas: o procedimento correto é criar um schema vazio, executar Flyway e iniciar com `ddl-auto=validate`.

O teste `DatabaseBaselineMySqlTest` automatiza `MySQL vazio → 13 migrations → 38 tabelas → Hibernate validate`, além das constraints de tenant, SKU/barcode, estoque central/condomínio, estoque fracionado negativo, snapshots, inventário, múltiplas tentativas, exclusividade ativa Mercado Pago e persistência de leads comerciais.
