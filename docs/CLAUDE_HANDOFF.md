# Handoff técnico — Backend App 24/7

> Auditoria feita em **14/10/2026** pelo Claude Code, ao assumir o projeto antes trabalhado com o Codex.
> Baseada no código, nas migrations, nas configurações, no histórico Git e em `24por7_contexto/`.
> Relatórios anteriores **não** foram tomados como prova: cada afirmação abaixo indica a evidência.
> Quando este documento e o código divergirem, o código vence — atualize este arquivo.

Legenda de estado usada neste documento:

- **Validado** — implementado e coberto por teste executado nesta auditoria (14/10/2026).
- **Não validado** — código presente, sem teste automatizado executado que o prove.
- **Parcial** — parte do comportamento existe; lacunas descritas.
- **Ausente** — não implementado.
- **Não verificável** — depende de ambiente/sistema externo não acessível nesta auditoria.

---

## 1. Situação do repositório (crítico — ler primeiro)

| Item | Estado (atualizado em 14/10/2026, após organização dos commits) |
|---|---|
| Branch | `master`, à frente de `origin/master` por `f040945`, `fd5ba3b` e pelos commits locais desta sessão (ver `git log origin/master..master`) — **nada enviado** |
| Outra branch local | `mpChek` (antiga, último commit `73ff4ae`) |
| Working tree | Todo o código, migrations V20–V22, testes, Compose, E2E e docs foram commitados. Permanece fora de commit apenas `24por7_contexto/.obsidian/workspace.json` (estado da UI do Obsidian) |

Histórico da auditoria inicial: até esta organização, todo o fechamento dos P0 de segurança
(fronteira HTTP deny-by-default, credencial individual de Terminal, WebSocket autenticado, anti-IDOR,
recuperação de senha com rate limit, reembolso Point) e as migrations V20–V22 existiam **somente no
working tree**. Agora estão no commit `869f575` (local).

- `origin/master` ainda termina a cadeia de segurança com `.anyRequest().permitAll()` e só tem as migrations
  V1–V13. Se algo foi implantado a partir de `origin/master`, o ambiente implantado tem rotas HTTP abertas.
  Não foi possível verificar o que está de fato rodando na EC2.
- Entrada pública (19/10/2026): ngrok → nginx (`deploy/nginx/`) → app; app e painel do ngrok só em
  `127.0.0.1`. Compose de produção é `deploy/docker-compose.yml` (fonte única); o workflow o envia com
  `deploy/nginx/` para `~/projetos/back-app247` no servidor Oracle e recria app, nginx e ngrok
  (ver `24por7_contexto/nginx.md`).
- Push em `master` dispara `.github/workflows/docker-publish.yml`: o job `test` (`./mvnw test`) roda primeiro e o
  job `deploy` (`needs: test`) faz build da imagem `jefiroo/app247-backend` (Dockerfile usa `-DskipTests`) e
  **deploy automático via SSH na EC2** (`docker compose pull app && up -d`).
- O push leva V14–V22 ao banco implantado na primeira subida: faça **backup e o preflight da V16 antes**
  (seção 4 e `backend-pending.md`).

---

## 2. Arquitetura e tecnologias

Fonte: `pom.xml`, `src/main/java/com/jefiro/app247`.

- Java 17, Maven Wrapper (`./mvnw`), **Spring Boot 4.0.6** (starters `webmvc`, `data-jpa`, `flyway`,
  `validation`, `security`, `data-redis`, `mail`, `websocket`, `actuator`).
- MySQL (`mysql-connector-j`) + Flyway (`flyway-mysql`); H2 somente para testes rápidos.
- Redis: filas de webhook/e-mail, deduplicação, códigos/tokens temporários, sessão de checkout, rate limits.
- JWT HMAC256 com `com.auth0:java-jwt` 4.5.2.
- Mercado Pago: `sdk-java` 2.1.29 + chamadas REST diretas à API Orders Point (`RestTemplate`).
- WebSocket nativo + STOMP (broker simples em memória).
- Springdoc OpenAPI 3.1.0 (Swagger em `/swagger-ui.html`; `/` redireciona).
- Apache POI (importação XLSX), ZXing (QR Code), Lombok.
- Testcontainers (`mysql:8.4.4`) para testes de schema/integração.

Estilo: monólito em camadas simples — controller → service → repository/SDK. Não há portas/adaptadores.
Injeção com `@Autowired` em campo é o padrão adotado (regra registrada em `24por7_contexto/00-index.md`).

### Organização de pacotes (`com.jefiro.app247`, 442 arquivos `.java`)

| Pacote | Conteúdo |
|---|---|
| `domain.model` (37) | Entidades JPA: `Empresa`, `Condominio`, `Produto`(+fiscal, códigos de barras), `EstoqueEmpresa`, `EstoqueCondominio`, `MovimentacaoEstoque`, `Transferencia*`, `Inventario*`, `Planograma*`, `Promocao*`, `Carrinho`, `CartItem`, `Order`, `OrderItem`, `PaymentAttempt`, `PaymentEvent`, `WebhookEvent`, `MercadoPagoConta(+Ativa)`, `TerminalPointBinding`, `TerminalTelemetry*`, `AuditLog`, `MarketingLead`, `CustomerIdentity`, `ImportacaoProduto*` |
| `domain.model.appclient` (11) | App Cliente: `AppClienteConfig`, `AppClientePreference`, `LoyaltyAccount`, `LoyaltyMovement`, `Reward`, `RewardRedemption`, `CustomerCoupon`, `CustomerIdentificationToken`, `PurchaseSupport`, `TenantLinkInvitation` |
| `domain.model.auth` | `User` (login por CPF; `getAuthorities` mapeia ADMIN→ADMIN+GERENTE+USER, GERENTE→GERENTE+USER, demais→USER), `RoleUser` (`ADMIN, GERENTE, PORTARIA, MORADOR, USER`), `Endereco` |
| `domain.model.terminal` | `Terminal` |
| `domain.model.dto*` | DTOs REST, admin, appclient, estoque, mercadopago, comprovante |
| `domain.model.enum_type` (36+3) | Estados persistidos como texto (`OrderStatus`, `CarrinhoStatus`, `PagamentoStatus`, `TerminalLifecycleState`...) |
| `infra.controller` (32+1) | Endpoints HTTP (seção 5) |
| `infra.service` (86) / `.admin` / `.comprovante` | Regras de negócio, integrações, workers e jobs |
| `infra.repository` (49) | Spring Data JPA + repositório Redis de sessão de checkout |
| `infra.security` (6) | `SecurityConfig`, `SecurityFilter` (JWT), `TerminalHttpAuthenticationFilter`, `AuthenticatedTerminal`... |
| `infra.config` (12) | CORS, Redis, OpenAPI, WebSocket nativo/STOMP, handshake/interceptors de Terminal |
| `infra.websocket` | `TerminalWebSocketHandler`, `PaymentWebSocketHandler` |
| `infra.event` | Eventos internos (reserva/liberação de estoque, fidelidade, e-mail, catálogo) |
| `infra.exception` | `RestExceptionHandler`, `RestErrorMessage`, `ApiBusinessException` |

Mapa por classe: `24por7_contexto/mapa-projeto.md`; visão de camadas: `24por7_contexto/arquitetura.md`
(com trechos desatualizados, ver seção 11).

---

## 3. Configurar, compilar, executar e testar

### Pré-requisitos
JDK 17, Docker (para testes Testcontainers e para MySQL/Redis locais). Maven não é necessário (`./mvnw`).

### Variáveis de ambiente
Nomes em `.env.example` e `docker-compose*.yml`; propriedades em `src/main/resources/application*.properties`.
Valores reais ficam em `.env` (ignorado pelo Git — **não ler/expor**).

Obrigatórias para subir: `JWT` (`api.secret.token`), `IDENTITY_HASH_SECRET` (≥ 32 chars; fail-fast em
`CustomerIdentityHashService`), `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` (perfil `prod`), `REDIS_HOST`/`REDIS_PORT`,
`WEBSOCKET_ALLOWED_ORIGINS` (o Compose aborta sem ela).

Integrações: `MP_CLIENT_ID`, `MP_CLIENT_SECRET`, `MP_OAUTH_REDIRECT_URL`, `MP_WEBHOOK_SECRET`, `MP_WEBHOOK_URL`,
`MP_API_BASE_URL` (default API oficial; o E2E aponta para stub), `MP_POINT_EXPIRATION_TIME` (default `PT5M`),
`SMTP_*`, `COMPROVANTE_SERVICE_URL`/`COMPROVANTE_INTERNAL_TOKEN`/timeouts, `APP_CORS_ALLOWED_ORIGIN_PATTERNS`,
`app.terminal.anomaly.*` (alerta de origem da credencial de Terminal; padrão ligado),
`PASSWORD_RECOVERY_*`, `TENANT_LINK_INVITATION_TTL`, `APP247_TERMS_*`/`APP247_PRIVACY_*`,
`TEST_ENDPOINTS_ENABLED` (default `false`; ignorado no profile `prod`),
`PAYMENT_RECONCILIATION_*`, `PRODUCT_IMPORT_*`, `CUSTOMER_LINK_*`.

Lacuna: `.env.example` não lista `SMTP_*`, `MP_*`, `COMPROVANTE_*`, `DB_URL/DB_USERNAME/DB_PASSWORD`, usados pelo Compose.

### Perfis
- Sem perfil: `application.properties` **não define datasource**; sempre ative `dev` ou `prod`.
- `dev` (`application-dev.properties`): MySQL em `127.0.0.1:${MYSQL_PORT:3306}/${MYSQL_DATABASE:app247}` (ou `DB_URL`),
  usuário/senha de `DB_USERNAME`/`DB_PASSWORD` ou `MYSQL_USER`/`MYSQL_PASSWORD` (sem default), Flyway ligado,
  Redis em `127.0.0.1:6379`. **Validado em 14/10/2026**: boot com MySQL 8.4/Redis 8 descartáveis, 22 migrations
  aplicadas, `/actuator/health` = `UP`.
- `prod`: MySQL via `DB_URL`, Flyway ligado, `ddl-auto=validate`. É o perfil usado no Docker e no E2E.
- `test` (`src/test/resources/application-test.properties`): H2 em memória, `ddl-auto=create-drop`, Flyway desligado,
  workers desligados, secrets fictícios.

### Comandos
```bash
./mvnw test                          # suíte completa (H2 + Testcontainers MySQL se Docker disponível)
./mvnw -Dtest=NomeDaClasseTest test  # classe específica
./mvnw package -DskipTests           # jar (o Dockerfile faz isso)
docker compose -f docker-compose.test.yml up --build   # API + MySQL 8.4 + Redis 8 + comprovante-service
./scripts/e2e-terminal-backend.sh    # E2E com Terminal Python real (exige ../TerminalPython/.venv)
```
Executar localmente: suba MySQL/Redis com `docker compose -f docker-compose.test.yml up -d mysql redis` e rode
`SPRING_PROFILES_ACTIVE=dev MYSQL_USER=... MYSQL_PASSWORD=... JWT=... IDENTITY_HASH_SECRET=... ./mvnw spring-boot:run`
(Flyway migra o banco apontado; não use um banco com dados que você não queira migrar). O E2E usa `prod` + `DB_URL`
(`scripts/e2e-terminal-backend.sh:75-88`).

Compose:
- `deploy/docker-compose.yml`: `app` + `nginx` + `ngrok` + `redis` + `comprovante-service`; banco **externo** via `DB_URL`
  (formato de produção/EC2).
- `docker-compose.test.yml`: stack completa com MySQL local.
- `docker-compose.e2e.yml`: MySQL/Redis efêmeros em `tmpfs` para o E2E.

---

## 4. Banco de dados e migrations

- Diretório: `src/main/resources/db/migration`, **V1 a V22** (1742 linhas SQL).
  `V1__core_tenant` … `V19__order_price_payment_attempt_and_terminal_authorization`,
  `V20__terminal_device_credentials`, `V21__customer_terminal_link`, `V22__point_refund_and_loyalty_reversal`.
- V20–V22 foram commitadas localmente em `869f575`; `origin/master` tem apenas V1–V13 (o próximo deploy aplica V14–V22).
- A cadeia antiga V1–V26 foi substituída por uma baseline nova; bancos criados com a cadeia antiga **não**
  são compatíveis (docs: `24por7_contexto/database.md`, `00-index.md`). O Compose usa volume `mysql_data_v2`.
- Produção: `ddl-auto=validate` — toda mudança de entidade exige migration.
- Tipos: dinheiro `DECIMAL(15,6)` + `BigDecimal` (`financial-rules.md`), UUID como `VARCHAR`/`String`,
  tempo em UTC (`hibernate.jdbc.time_zone=UTC`, `connectionTimeZone=UTC`).
- Multi-tenant reforçado no schema com FKs compostas (`empresa_id`) nos agregados críticos.
- **Validado:** `DatabaseBaselineMySqlTest` (12 testes) aplicou as 22 migrations em `mysql:8.4.4` via Testcontainers
  ("Successfully applied 22 migrations … now at version v22").
- **Não verificável:** compatibilidade com o histórico Flyway do(s) banco(s) já implantado(s). Pendência P1
  de preflight de dados antes da V16 (normalização de CPF/e-mail pode colidir) segue aberta.
- Documentos: `database.md` (atual), `database-er.md`, `database-retention.md`. `database-atual.md`,
  `database-melhorias.md`, `banco-de-dados.md` são **históricos** (cadeia antiga).

---

## 5. Endpoints e autorização

Fonte: `infra/controller/*` e a matriz em `infra/security/SecurityConfig.java`.
Catálogo detalhado com payloads: `24por7_contexto/api.md`; contratos com clientes: `client-contracts.md`,
`terminal-integration.md`.

### Identidades
- **Humano (JWT Bearer)**: `SecurityFilter` valida token, confere `userId`/`empresaId` com o estado atual do usuário e
  da Empresa (desativada → 403 `COMPANY_DISABLED`), preenche `EmpresaContext` (limpo no fim da requisição).
  Roles: `ADMIN`, `GERENTE` (gestores), `USER` (cliente). ADMIN/GERENTE também têm `ROLE_USER`.
- **Terminal (device credential)**: `TerminalHttpAuthenticationFilter` lê a credencial individual do Terminal
  (header `X-Terminal-Token`), concede `ROLE_TERMINAL`; ausente → 401 `AUTH_REQUIRED`, inválida → 401
  `TERMINAL_AUTH_INVALID`. Credencial gerada/rotacionada/revogada pelo gestor; armazenada como hash (V20).
- Erros de autenticação em JSON (`RestErrorMessage`) com `code`.

### Matriz (ordem importa; regra final `anyRequest().denyAll()`)

| Acesso | Rotas |
|---|---|
| Público | `GET /`, `/error`, `/actuator/health`, `/terminal/health`, `/terminal/serial/*`, `POST /terminal/pairing` e `/terminal/pairing/claim` (limite por origem), `/webhook/mercadopago` (HMAC próprio), `/mercado-pago/oauth/callback`, `/mp/oauth/callback`, `/public/leads`, `/public/app-config`, Swagger/OpenAPI, `POST /onboarding` e alias `POST /condominio`, `POST /auth/{register,login,login/admin,send-code,validate-code}`, `POST /user/{recuperar,validar,redefinir-senha}` |
| Negado sempre | demais `/auth/**`, `GET /checkout` (vínculo legado por `userId`), `/checkout/session` |
| `TERMINAL` | `/terminal-socket`, `/payment-socket/**`, `/ws/**`, `/produtos/sync`, `/produtos/home`, `/terminais/*/produtos-disponiveis`, `/terminal/telemetry`, `/terminal/*/bootstrap`, `/terminal/*/factory-reset/**`, `/terminal/customer-link/consume`, `/terminal/coupons/apply`, `/carrinho/**`, `/order/**`, `/pagamento/terminal/**`, `/checkout/carrinho`, `/checkout/qrcode`, `/comprovante/**` |
| `ADMIN`/`GERENTE` | `/admin/**` (dashboard, alertas, pagamentos, vendas, atividade, **refund** `POST /admin/orders/{orderId}/refund`, App Cliente config/fidelidade/recompensas, convites de vínculo), `/empresas/**`, `/condominios/**` (incl. estoque por condomínio), `/terminais/**` (CRUD, credencial, vínculo Point, telemetria/monitoramento), `/produtos/**` (CRUD, importação XLSX), `/promocoes/**`, `/estoque-empresa/**`, `/estoque/geral`, `/transferencias-estoque/**`, `/inventarios/**`, `/planogramas/**`, `/mercado-pago/**`, `/mp/oauth/{mercadopago,terminal}/**`, `/api/testes/mercadopago/**` |
| `USER` | `/app-cliente/**` (bootstrap, catálogo, compras, comprovante PNG/envio, tokens de identificação, fidelidade, recompensas/resgate, cupons/tokens, preferências, suporte, `tenant-link`), `/user/**` (alterar senha, foto, perfil, pedidos — com ownership/tenant via `HumanRequestAuthorizationService`) |

Pontos de atenção da API:
- Rotas legadas com verbo no path e mutação via `GET`: `GET /order/finalizar`, `GET /pagamento/terminal/{carrinho_id}`
  (inicia cobrança; existe também `POST` equivalente). Mudar exige coordenar com o Terminal Python.
- `/api/testes/mercadopago/**` (`TestarWebhookMPController`) faz chamadas reais ao MP; registrado só com
  `app.test-endpoints.enabled=true` (default `false`) e nunca no profile `prod` (`@Profile("!prod")`); exige ADMIN/GERENTE.
- OpenAPI só documenta Bearer/erros de forma completa nas rotas novas (`/admin/app-cliente/**`).

### WebSocket
- Nativo: `/terminal-socket` (heartbeat/lifecycle) e `/payment-socket/{terminalId}` (eventos de pagamento) —
  `TerminalWebSocketConfig`.
- STOMP: `/ws`, broker `/topic`, prefixo `/app`; eventos em `/topic/payment/{terminalId}` — `WebSocketConfig`.
- Handshake exige credencial de Terminal + `TerminalHandshakeInterceptor`; STOMP CONNECT/SUBSCRIBE/SEND checados
  por `TerminalStompAuthorizationInterceptor`; origins de `WEBSOCKET_ALLOWED_ORIGINS` (`*` é descartado).
- Docs: `websocket.md`, `security/websocket-security.md`, `security/stomp-security.md`.

---

## 6. Regras de negócio principais

- **Tenant**: `Empresa` é a fronteira. `Empresa 1—N Condominio 1—N Terminal`; Terminal não tem FK direta para
  Empresa (deriva de `condominio.empresa`). Services usam consultas compostas recurso+empresa. Decisão:
  `24por7_contexto/decisoes/001-*`.
- **Onboarding**: `OnboardingService.executar` cria Empresa, gestor ADMIN, primeiro Condomínio e Terminal numa transação.
- **Catálogo/estoque**: Produto por Empresa; estoque central (`EstoqueEmpresa`) e por condomínio
  (`EstoqueCondominio`), ledger `MovimentacaoEstoque` com chave idempotente, lock pessimista; saldo pode ficar negativo
  (com warning). Transferências, inventário cego, planograma, importação XLSX assíncrona. Docs: `estoque.md`,
  `inventario.md`, `importacao-produtos.md`.
- **Sync do Terminal**: full/incremental por cursor UTC + invalidação WebSocket após commit (`sincronizacao-produtos.md`).
- **Promoções**: por Empresa ou Condomínio, transições temporais por scheduler (`promocoes.md`).
- **Venda**: Terminal cria `Carrinho` (`OPEN`) → `Order` única por carrinho com snapshot imutável em `OrderItem`,
  preço finalizado/congelado (`payment/order-price-finalization.md`) e reserva de estoque.
- **Pagamento Point**: `PaymentAttempt` local criada com lock (uma Order intermediária por Terminal), chamada
  `/v1/orders` fora da transação com chave idempotente persistida, resposta inicial `WAITING_PAYMENT`.
  Webhook HMAC → dedupe Redis → fila `mp_queue` (processing, 3 tentativas, DLQ) → `PaymentWorker` → máquina de
  estados monotônica com versão externa e lock. Várias tentativas por Order; `UNKNOWN` resolvido por reconciliação
  (`PaymentReconciliationService`: scheduler, startup e consulta HTTP). Aprovação finaliza tentativa/Order/Carrinho e
  confirma estoque; cancelamento/falha libera reserva. Docs: `mercado-pago.md`, `payment-*.md`, `payment/*.md`.
- **Reembolso Point** (V22): integral, idempotente, recuperável; devolve estoque e estorna pontos/cashback
  (`payment/point-refund.md`).
- **Conta MP**: OAuth por Empresa (`MercadoPagoConta`, exclusividade ativa em `MercadoPagoContaAtiva`); maquininha
  física no `Terminal` (`mercadoPagoTerminalId`/`TerminalPointBinding`). Decisão `decisoes/002-*`.
- **App Cliente**: cadastro público por CPF (HMAC `IDENTITY_HASH_SECRET`), vínculo conta↔tenant por convite,
  claim de compras históricas, fidelidade `NONE|POINTS|CASHBACK`, recompensas, cupons com snapshot,
  QR opaco single-use de identificação no Terminal (V21) vinculando Carrinho→Order→fidelidade.
- **Comprovante**: Spring monta snapshot e envia JSON ao FastAPI `comprovante-service`, que gera PNG e aciona n8n
  (`comprovante-compra.md`); idempotência em Redis.
- **Recuperação de senha**: anti-enumeração, rate limit por IP/CPF, código/token com TTL e uso único
  (`security/password-recovery-security.md`).
- **Jobs agendados** (`@Scheduled`): workers de webhook e e-mail (2 s), reconciliação de pagamentos (15 s),
  presença/offline de Terminal, telemetria (offline-check e retenção), transições de promoção, recuperação de
  importação, retenção de dados (cron 03:40).

---

## 7. Integrações externas

| Integração | Onde | Estado |
|---|---|---|
| Mercado Pago OAuth + Orders Point + webhook | `OauthMercadoPagoService`, `MercadoPagoCobrancaService`, `MercadoPagoOrderQueryService`, `PaymentWorker`, `MercadoPagoWebhookSignatureService` | Validado com mocks/stub; **não verificável** contra MP real nesta auditoria. Refresh automático do token OAuth (`MercadoPagoTokenRefreshService`, 6 h, 30 dias antes de vencer) testado com HTTP simulado. |
| PIX / Checkout Pro | — | **Ausente**: a classe `MercadoPagoService` citada em `contexto.md` não existe mais no código. |
| FastAPI `comprovante-service` → n8n | `infra/service/comprovante/*` | Testes mockam `RestTemplate`; ponta a ponta **não validado**. |
| SMTP (JavaMail) | `EmailWorker` + fila Redis | Não verificável (sem SMTP real). |
| Redis | filas, dedupe, rate limits, sessões | Desligado nos testes unitários; usado no E2E de 28/09 (não reexecutado). |
| Terminal Python (outro repo) | HTTP + WebSocket com credencial individual | Contrato em `terminal-integration.md`; E2E de 28/09/2026 documentado em `e2e-terminal-backend.md` (não reexecutado aqui; doc cita "20 migrations", hoje são 22). |
| Flutter admin / App Cliente (outros repos) | REST + JWT | Contratos em `client-contracts.md`, `app-cliente.md`, `flutter-backend-gap.md`. |
| Armazenamento de imagens | OCI Object Storage via API S3 (`ObjectStorageService`), URL pública no banco | Bucket precisa de leitura pública. |

---

## 8. Estado real das funcionalidades

| Área | Estado | Evidência |
|---|---|---|
| Compilação | **Validado** | `./mvnw -o test` exit 0 (estado hoje commitado em `869f575`) |
| Schema Flyway V1–V22 em MySQL 8.4 | **Validado** | `DatabaseBaselineMySqlTest` (Testcontainers) |
| Matriz HTTP deny-by-default | **Validado** | `ResidualHttpAuthorizationIntegrationTest`, `AdminSecurityTest`, `SecurityFilterTest`, `TerminalOperationalSecurityIntegrationTest` |
| Credencial individual de Terminal / rotação | **Validado** | `TerminalDeviceCredentialIntegrationTest`, `TerminalHandshake*Test`, `TerminalStompAuthorizationInterceptorTest` |
| Anti-IDOR usuário / ownership | **Validado** | `ResidualHttpAuthorizationIntegrationTest`, `AppClienteEndpointsIntegrationTest` |
| Recuperação de senha | **Validado** | `PasswordRecoverySecurityIntegrationTest` (MySQL) |
| Onboarding, hierarquia, tenant | **Validado** | `OnboardingServiceTest`, `HierarquiaTenantServiceTest`, `AdminRepositoryTenantTest` |
| Carrinho/Order/preço/cupom | **Validado** (unit/integração) | `CarrinhoServiceTest`, `OrderServiceTest`, `OrderPriceFinalizationServiceTest`, `PricingServiceTest` |
| Pagamento Point (submissão, cancelamento, reconciliação, webhook) | **Validado com mocks**; real MP **não verificável** | `PointPayment*Test`, `PaymentReconciliationServiceTest`, `PaymentWorkerTest`, `MercadoPagoWebhookSignatureServiceTest` |
| Reembolso Point + estorno de fidelidade | **Validado com mocks** | `PointPaymentRefundServiceTest`, `PaymentRefundPersistenceServiceTest`, `LoyaltyRefundReversalTest` |
| QR de identificação do cliente no Terminal | **Validado** | `TerminalQrServiceTest`, `CustomerLinkConcurrencyIntegrationTest`, `TerminalQrEndpointsIntegrationTest` |
| Estoque, transferências, inventário, importação | **Validado** | `EstoqueServiceTest`, `CentralStockTransferIntegrationTest`, `InventoryAndProductImportIntegrationTest` |
| Telemetria / lifecycle Terminal | **Validado** (unit) | `Telemetry*Test`, `TerminalLifecycleServiceTest`, `TerminalPresenceServiceTest` |
| App Cliente config/fidelidade/recompensas/cupons | **Validado** | `AdminAppClienteManagementIntegrationTest` (MySQL), `AppClienteEndpointsIntegrationTest` |
| Comprovante Spring→FastAPI→n8n | **Parcial** — Spring validado com mock; ponta a ponta não verificado | `comprovante/*Test` |
| Visualização vs envio de comprovante no mesmo snapshot | **Parcial** | `backend-pending.md` |
| Ledger de pontos tipado/expiração | **Parcial** | `backend-pending.md` |
| Consumo de cashback | **Ausente** | `backend-pending.md` |
| Workflow de suporte de compra | **Parcial** (só abertura `OPEN`) | `backend-pending.md` |
| Termos/Privacidade com aceite | **Ausente/operacional** (URLs vazias por padrão; Compose não repassa `APP247_*`) | `docker-compose*.yml`, `LegalDocumentsService` |
| Refresh OAuth MP | Implementado (20/10/2026), não verificado contra MP real | docs `mercado-pago.md` |
| Checkout Pro / PIX | **Ausente** | `MercadoPagoService` não existe em `infra/service` |
| Perfil `dev` | **Validado** (boot manual com MySQL/Redis descartáveis) | `application-dev.properties` |
| Endpoints de teste MP desligados por padrão e em `prod` | **Validado** | `TestarWebhookMPControllerRegistrationTest` |
| Recuperação de itens presos em `processing` após crash | **Parcial** | `auditoria-resumo.md` |

---

## 9. Testes

Executado nesta auditoria (14/10/2026), Java 17.0.20, Docker disponível:

```
./mvnw -B -q -o test          → EXIT=0
target/surefire-reports       → 88 classes, 415 testes, 0 falhas, 0 erros, 0 ignorados
Testcontainers mysql:8.4.4    → "Successfully applied 22 migrations ... now at version v22"
git diff --check              → apenas linha em branco extra no fim de .env.example e Dockerfile
```

- O resultado vale para o código hoje commitado localmente (`869f575` em diante), não para `origin/master`.
- A maioria usa H2 + Flyway desligado; 3 classes usam MySQL real via Testcontainers
  (`DatabaseBaselineMySqlTest`, `AdminAppClienteManagementIntegrationTest`, `PasswordRecoverySecurityIntegrationTest`).
  Sem Docker essas são **ignoradas** (`disabledWithoutDocker = true`), não aprovadas.
- Não executados nesta auditoria: `scripts/e2e-terminal-backend.sh` (depende do repositório Terminal Python),
  Redis real, SMTP, Mercado Pago real, FastAPI/n8n.
- Segunda execução com Redis inacessível (`-Dspring.data.redis.port=1`), simulando o runner do GitHub sem Redis:
  EXIT=0, 0 falhas. Contou 427 testes porque incluía `UserServiceTest.java`, um esqueleto da IDE com 12 `@Test`
  vazios (retirado do Git, mantido local). Testes reais: 415.
- Após desligar os endpoints de teste e corrigir o perfil `dev`: `./mvnw -o clean test` → EXIT=0, 421 testes, 0 falhas,
  0 ignorados, Flyway V1–V22 em MySQL. Destes, 420 pertencem ao estado commitado (415 + 5 de
  `TestarWebhookMPControllerRegistrationTest`) e 1 a `RegistrationServiceTest`, que estava em edição fora desta sessão.
- O CI (`docker-publish.yml`) agora tem o job `test` (`./mvnw test`, Java 17 Temurin, cache Maven) antes do `deploy`.
  Ainda **não executado no GitHub**.

---

## 10. Segurança — observações

- Secrets: `.env`/`.env.*` ignorados; `application*.properties` só referenciam variáveis. Documentação antiga registra
  que segredos chegaram a ser versionados no histórico → **rotação externa ainda pendente** (não verificável aqui).
- JWT com TTL fixo de **200 h** (`TokenService`), sem issuer/audience/refresh/revogação (pendência P3).
  Token inválido/expirado responde 401 `TOKEN_INVALID`.
- Limites (18/10/2026): login por CPF/origem, send-code e validate-code por e-mail/origem, onboarding por
  origem (`PublicAuthRateLimits`). Gestor não altera e-mail de terceiros e GERENTE não altera ADMIN.
- Webhook MP (18/10/2026): estado sempre consultado em `GET /v1/orders/{id}` com a conta da Empresa da tentativa;
  o corpo do webhook não é usado como fonte de estado.
- `RestExceptionHandler`: mensagens de exceções lançadas por bibliotecas (JDK/Spring/Hibernate) viram texto
  genérico; mensagens das regras do app continuam indo ao cliente.
- Endpoints de teste MP (chamadas reais): opt-in por `TEST_ENDPOINTS_ENABLED=true`, nunca em `prod`, exigem
  ADMIN/GERENTE. A classe ainda é compilada no jar.
- Migração legada de credencial (`/terminal/*/credential/migrate`, `TERMINAL_INTERNAL_TOKEN`) removida em
  18/10/2026; `GET /terminal/serial/*` passou a expor só `terminalId`, `serialNumber`, `ativo`, `activated`.
- Pareamento (20/10/2026): o Terminal mostra código/QR, o gestor confirma no app e o equipamento recebe a
  credencial sozinho (`TerminalPairingService`, sessões no Redis). Detalhes em `24por7_contexto/terminal-integration.md`.
- Uso da mesma credencial de Terminal a partir de origens diferentes gera `audit_log`
  `TERMINAL_CREDENTIAL_ORIGIN_CHANGED` (`TerminalCredentialAnomalyService`); só alerta, não bloqueia.
- Cadastro de cliente usa `X-Forwarded-For` para rate limit (`AuthController.clientIp`), enquanto a recuperação de
  senha usa só `getRemoteAddr()` — inconsistência; `X-Forwarded-For` é falsificável sem proxy confiável.

---

## 11. Divergências documentais (`24por7_contexto/`)

Corrigidas em 14/10/2026 após verificação no código:

| Documento | Afirmação antiga | Correção aplicada |
|---|---|---|
| `contexto.md`, `arquitetura.md` | Rotas HTTP/administrativas "públicas temporariamente" | Matriz por role e `anyRequest().denyAll()` |
| `auditoria-resumo.md` | `anyRequest().permitAll()`, 68 testes, "V21–V23" | Aviso "Superado em 14/10/2026" no topo (documento mantido como histórico) |
| `arquitetura.md` | "CORS aceita qualquer origem" | `APP_CORS_ALLOWED_ORIGIN_PATTERNS` (default localhost) |
| `arquitetura.md` | Workers sem contador/DLQ | `:processing` + 3 tentativas + `:dlq` |
| `arquitetura.md` | `/auth/register` exige gestor | Cadastro público de cliente com rate limit |
| `arquitetura.md` | Baseline "V1–V11" | V1–V22 |
| `contexto.md` | `MercadoPagoService` implementa PIX/Checkout Pro | Classe inexistente; só Point |
| `contexto.md`, `api.md`, `mercado-pago.md`, `mapa-projeto.md` | Endpoints de teste habilitados por padrão / desligados pela propriedade em `prod` | Default `false` e `@Profile("!prod")` |

`e2e-terminal-backend.md` cita "20 migrations" por registrar a execução de 28/09/2026 (correto para aquela data).

`backend-pending.md` é o backlog mais confiável (itens P0 marcados como concluídos batem com o código).
`backend-review-2026-09.md`, `auditoria-bugs.md`, `melhorias.md` são fotografias anteriores; reconferir antes de usar.

---

## 12. Pendências e débitos técnicos (priorizados)

1. **Push e deploy controlados** (P0 operacional): commits locais prontos; antes do push, backup do banco
   implantado + preflight V16, e confirmar que os Terminais em campo já usam credencial individual
   (`SELECT id FROM terminal WHERE ativo = 1 AND device_credential_hash IS NULL;` deve vir vazio — a
   migração legada foi removida em 18/10/2026; Terminal sem credencial precisa de provisionamento manual).
2. Confirmar a primeira execução do job `test` no GitHub Actions (nunca rodou lá).
3. **Rotação de secrets** historicamente versionados (externo).
4. **Preflight V16** e validação do Flyway contra cópia dos bancos implantados.
5. ~~Perfil `dev`~~ e ~~endpoints de teste ligados por padrão~~ — resolvidos em 14/10/2026.
6. Opcional: tirar `TestarWebhookMP*` do jar de produção (hoje só não é registrado).
7. JWT: TTL configurável e decisão sobre refresh/revogação (401 `TOKEN_INVALID` para token inválido feito em 18/10/2026).
8. ~~Refresh automático do token OAuth Mercado Pago~~ (feito em 20/10/2026; confirmar a primeira renovação real no AuditLog `MERCADO_PAGO_TOKEN_REFRESHED`).
9. Recuperação automática de mensagens presas em `processing` no Redis.
10. Produto: cashback consumível, ledger de pontos tipado, workflow de suporte, termos/privacidade com aceite,
    comprovante unificado, paginação do catálogo App Cliente no banco, política de saldo zero/negativo.
11. OpenAPI: documentar roles/credencial de Terminal/erros nas rotas legadas.
12. Rotas legadas com mutação via `GET` (`/order/finalizar`, `/pagamento/terminal/{id}`) — requer coordenação com o Terminal.

---

## 13. Próximos passos recomendados

1. Decidir o push: backup + preflight V16 no banco implantado, plano de migração das credenciais dos Terminais,
   e então `git push origin master` (dispara testes e deploy).
2. Acompanhar a primeira execução do workflow com o job `test`.
3. Seguir `24por7_contexto/backend-pending.md`: P1 preflight V16 (script de verificação de CPF/e-mail duplicados
   antes do deploy) → P3 JWT (TTL configurável) → P2.
