# Dashboard administrativo e cadastro de Produto

Voltar para [[00-index]]. Contratos HTTP relacionados em [[api]], segurança em
[[autenticacao]] e regras de estoque em [[estoque]].

## Diagnóstico anterior à implementação

### Home

A Home Flutter já possuía shell responsiva, Design System, `BrandingConfig` e
alguns cards de telemetria. Porém, ela fazia cinco requisições independentes,
montava o checklist no cliente, mostrava apenas indicadores de Terminais e
tratava alertas ativos como se fossem atividade recente. Não havia faturamento,
vendas, pagamentos em atenção nem uma visão administrativa composta.

### Produto

O mesmo widget atendia criação e edição e já integrava SKU, nome, descrição,
preço decimal, categoria, unidade, peso, tolerância, múltiplos barcodes e fiscal.
O layout, entretanto, era uma única sequência estreita de inputs. Não havia
upload de foto multiplataforma, estoque central inicial, disponibilidade em
múltiplos condomínios na própria tela ou acesso contextual a Promoções.

### Backend auditado

| Necessidade | Contrato encontrado no estado oficial auditado | Ação desta integração |
|---|---|---|
| Produto CRUD | `POST/GET/PUT/PATCH /produtos` | reutilizado e evoluído |
| Barcodes e fiscal | partes do DTO de Produto | reutilizados e completados |
| Estoque inicial | `POST /estoque-empresa/{produtoId}/entrada` | reutilizado |
| Associação a condomínio | `POST /condominios/{id}/estoque` e `DELETE /condominios/{id}/estoque/{produtoId}` | reutilizado |
| Promoções | `/promocoes` | reutilizado por navegação |
| Dashboard | `GET /admin/dashboard` | reutilizado sem criar CRUD paralelo |
| Checklist | `GET /admin/onboarding-status` | reutilizado como agregação derivada |
| Resumo de Terminais | `GET /admin/terminals/summary` | reutilizado sobre as regras de telemetria |
| Resumo/lista de alertas | `GET /admin/alerts/summary` e `/admin/alerts` | reutilizado como visão tenant-wide |
| Resumo/lista de vendas | `GET /admin/sales/summary` e `/admin/sales` | reutilizado com agregações/paginação |
| Pagamentos em atenção/lista | `GET /admin/payments/attention-summary` e `/admin/payments` | reutilizado sobre `PaymentAttempt` |
| Atividade recente | `GET /admin/activity` | reutilizado como projeção filtrada do `AuditLog` |

Não foi criado `/admin/produtos`: o CRUD oficial permanece em `/produtos`.

## Endpoints administrativos

Todos exigem JWT de usuário `ADMIN` ou `GERENTE`. A Empresa é obtida por
`EmpresaContext`; nenhum deles aceita `empresaId` como autoridade.

Para o cliente Web, CORS aceita por padrão somente `localhost`/`127.0.0.1`.
Ambientes publicados configuram `APP_CORS_ALLOWED_ORIGIN_PATTERNS` como
allowlist separada por vírgulas.

| Método e rota | Resposta/objetivo |
|---|---|
| `GET /admin/dashboard` | snapshot composto para a Home |
| `GET /admin/onboarding-status` | checklist derivado do estado real |
| `GET /admin/terminals/summary` | total ativo, online, offline e com alerta |
| `GET /admin/alerts/summary` | ativos, info, warning e critical |
| `GET /admin/alerts` | alertas paginados, filtro `status` |
| `GET /admin/payments/attention-summary` | tentativas que realmente exigem atenção |
| `GET /admin/payments` | histórico paginado com período, status, provider, Terminal e Order |
| `GET /admin/sales/summary` | quantidade, faturamento e ticket médio |
| `GET /admin/sales` | histórico paginado com período, condomínio, Terminal e status |
| `GET /admin/activity` | eventos administrativos filtrados do `AuditLog` |

Períodos nomeados válidos para vendas são `TODAY`, `7D` e `30D`. Também são
aceitos `from` e `to` completos, com intervalo máximo de um ano. Paginação
limita cada página a no máximo 100 itens e rejeita campos de ordenação não
permitidos.

## DTOs

O dashboard não retorna entities JPA. Os contratos administrativos encontrados e
integrados são:

- `AdminDashboardResponse`;
- `AdminSalesSummaryResponse` e `AdminSaleResponse`;
- `AdminTerminalSummaryResponse`;
- `AdminAlertSummaryResponse` e `AdminAlertResponse`;
- `AdminPaymentAttentionResponse` e `AdminPaymentResponse`;
- `AdminStockSummaryResponse`;
- `AdminOnboardingStatusResponse`;
- `AdminActivityResponse`.

Produto ganhou a projeção `ProdutoCondominioDisponibilidadeResponse` para a
tela editar associações sem baixar estoques de todos os condomínios.

## Origem e regra dos KPIs

### Vendas e faturamento hoje

`OrderRepository.summarizeSales` executa `COUNT` e `SUM` no banco. Somente
Orders `PROCESSED` com `paidAt` dentro do dia operacional são válidas. O ticket
médio é calculado no backend com `BigDecimal`, escala seis. O histórico aberto
pelo card usa o mesmo status e filtra o período por `paidAt`.

### Terminais

O resumo conta Terminais ativos. Online significa `lastPing` posterior ao
limite calculado com `TelemetryThresholds.offlineSeconds`; offline é a diferença
entre total ativo e online. O Flutter não replica o threshold.

### Alertas

As contagens vêm de `TerminalTelemetryAlert`. Alertas de Terminal offline ou
com estado operacional `CRITICO`/`OFFLINE` são críticos; os demais ativos são
warning. A listagem é paginada e inclui somente Terminais do tenant.

### Pagamentos em atenção

A consulta considera apenas a tentativa mais nova de cada Order. Entram na
contagem:

- `ACTION_REQUIRED`;
- `PENDING` cujo `updatedAt` é anterior ao threshold configurável
  `admin.dashboard.payment-pending-attention-minutes` (15 minutos por padrão);
- `FAILED` com `statusDetail=processing_error`.

Um `PENDING` recente não é tratado como falha.

### Estoque baixo

O domínio ainda não possui estoque mínimo por local aplicável ao saldo real.
`PlanogramaProduto.quantidadeMinima` não define sozinho uma regra tenant-wide de
estoque baixo. Por isso o DTO retorna `regraConfigurada=false`, contagem nula e
motivo explícito. A interface mostra “Não disponível”; não existe limite
inventado no Flutter.

## Checklist de implantação

Nenhum boolean é persistido. `AdminOnboardingService` deriva:

- Empresa existente no contexto;
- Condomínio ativo;
- Terminal ativo;
- conta Mercado Pago vinculada;
- Point vinculada;
- Produto ativo;
- Produto ativo associado a um Condomínio por `EstoqueCondominio` ativo.

Cada item da Home navega para a tela capaz de resolver a pendência.

## Atividade recente

`AdminActivityQueryService` filtra na própria query as ações conhecidas do
`AuditLog`, aplica títulos e destinos administrativos e não devolve
`before_data`, `after_data`, metadata ou logs técnicos crus. Não foi criada uma
segunda tabela de histórico. Eventos
de Terminal offline e reconciliação de pagamento ainda não aparecem porque
esses fluxos não produzem hoje uma ação de negócio compatível no `AuditLog`.

## Flutter

`DashboardPage` consome somente `GET /admin/dashboard`, oferece
pull-to-refresh no mobile e botão de atualização em telas maiores. Exibe seis
KPIs, checklist, bloco de atenção e atividade recente. Vendas, pagamentos e
alertas possuem telas paginadas próprias.

`ProductEditorPage` é o fluxo oficial atual de criação/edição e usa cards do Design
System. Em telas largas, Dados gerais e Preço formam duas colunas; no mobile as
seções ficam em uma coluna com ação fixa inferior. A tela integra foto por bytes
(sem `dart:io`), barcodes ativos/inativos e principal, fiscal recolhível,
estoque central inicial e associações a condomínios. A seção de Promoções
reutiliza `GET /promocoes?status=ATIVA&produtoId=...`, mostra somente as ativas
e mantém a edição no módulo específico.

Valores monetários continuam em `DecimalValue` textual; o Flutter não usa
`double` para cálculo financeiro crítico.

## Branding multi-tenant

`GET /empresas/me/branding` e `PUT /empresas/me/branding` usam exclusivamente
`EmpresaContext.require()`. O request contém `nomeExibicao`, logos HTTPS opcionais
e as cores `corPrincipal`, `corSecundaria` e `corDestaque` em `#RRGGBB`.
`AdminDashboardService` usa o mesmo nome de exibição, com fallback para nome
fantasia/razão social. A V11 adiciona os campos à Empresa com cores padrão App
24/7. Nenhum token ou secret integra esse DTO.

## Testes

Há testes de isolamento por Empresa e agregações de vendas/pagamentos, timezone,
threshold de Terminal, alertas, checklist, barcode por tenant, fiscal e
disponibilidade de Produto. No Flutter há cobertura de loading, erro, conteúdo,
checklist, criação/edição, validações, duplo submit, estoque inicial,
condomínios, fiscal, barcodes e layout em 360, 600, 1024, 1366 e 1920 pixels.
`EmpresaBrandingServiceTest` cobre fallback e atualização somente da Empresa do
contexto. `DatabaseBaselineMySqlTest` aplica e valida as onze migrations numa
base MySQL vazia. A execução final de `mvn -q test` aprovou 180 testes, sem
falhas, erros ou testes ignorados.
