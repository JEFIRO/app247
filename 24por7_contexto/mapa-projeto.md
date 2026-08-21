# Mapa do projeto

Voltar para [[00-index]].

```text
com.jefiro.app247
├── App247Application
├── domain.model
│   ├── entidades comerciais e tenant
│   ├── auth
│   ├── terminal
│   ├── dto
│   └── enum_type
└── infra
    ├── controller
    ├── service
    ├── repository
    ├── security
    ├── config
    ├── event
    ├── websocket
    ├── exception
    └── dto
```

## `domain.model`

- Tenant/operação: `Empresa`, `Condominio`, `Terminal`, `Endereco`.
- Catálogo/estoque: `Produto`, `GrupoTributario`, `EstoqueCondominio`, `MovimentacaoEstoque`.
- Compra: `Carrinho`, `Item`, `Order`, `Pagamento`.
- Integração: `MercadoPagoConta`, `WebhookEvent`.
- Identidade: `auth.User`, `auth.RoleUser`.

As classes concentram a maior parte das regras nos services. `Carrinho` possui helpers para manter a associação de itens, `Item` constrói snapshot comercial e `OrderStatus` define transições externas válidas.

## DTOs

- `domain.model.dto`: requests/responses REST genéricos, autenticação, sessão, estoque e Mercado Pago.
- `infra.dto.onboarding`: contrato do caso de uso de onboarding.
- `infra.dto.mercadopago`: resposta enriquecida de terminais e request de vínculo.
- `ProdutoListagemDTO` está no package de controller, uma inconsistência de organização.
- `AutenticacaoService`, apesar de ser service Spring, está dentro de `domain.model.dto.auth`.

## Controllers

| Controller | Responsabilidade real |
|---|---|
| `AuthController` | login, cadastro comum e código de e-mail |
| `UserController` | recuperação, senha, consulta/alteração e foto |
| `OnboardingController` | cadastro inicial completo e alias legado |
| `EmpresaController` | consulta/alteração da empresa autenticada |
| `CondominioController` | CRUD parcial tenant-aware |
| `TerminalController` | ativação pública, CRUD administrativo e vínculo Point |
| `ProdutoController` | catálogo, upload, sync e destaques |
| `EstoqueController` | disponibilidade, entrada, ajuste, lista e auditoria |
| `CarrinhoController` | criação e leitura do carrinho |
| `CheckoutSessionController` | sessão Redis e QR Code |
| `OrderController` | criação e consulta de Order |
| `PagamentoController` | início de cobrança Point |
| `OauthMercadoPagoController` | OAuth e listagem de maquininhas |
| `MercadoPagoWebhookController` | HMAC, deduplicação e fila |
| `TestarWebhookMPController` | operações externas de sandbox/debug |
| `FileController` | upload local |
| `DocController` | redirect Swagger |

Todas as rotas estão descritas em [[api]].

## Services de aplicação

- Gestão: `EmpresaService`, `CondominioService`, `TerminalService`, `OnboardingService`.
- Identidade: `UserService`, `TokenService`, `EmailService`, `EmailWorker`.
- Compra: `ProdutoService`, `CarrinhoService`, `OrderService`, `PagamentoService`, `EstoqueService`.
- Mercado Pago: `OauthMercadoPagoService`, `MercadoPagoTerminalService`, `MercadoPagoCobrancaService`, `MercadoPagoOrderQueryService`, `MercadoPagoWebhookSignatureService`, `MercadoPagoService`, `TestarWebhookMP`.
- Infraestrutura: `CheckoutSessionService`, `FileStorageService`, `QRCodeService`, `PaymentWorker`, `PaymentSocketService`.

`MercadoPagoService` contém PIX/Checkout Pro e código comentado; não participa das rotas atuais. `TestarWebhookMP` continua compilado, mas seu controller é condicionado por `app.test-endpoints.enabled` e fica desabilitado no profile de produção.

## Repositories

- JPA: empresa, condomínio, terminal, usuário, endereço, produto, carrinho, Order, pagamento, conta Mercado Pago, estoque, movimentação e webhook event.
- Redis manual: `CheckoutSessionRepositoryImpl`.
- Tenant-aware: principais consultas de condomínio, terminal, produto e estoque.
- Globais: CRUD base de usuário, carrinho, Order, pagamento, endereço e rotinas de terminal.

## Segurança e configuração

- `SecurityConfig`: matchers HTTP, stateless, CSRF desabilitado.
- `SecurityFilter`: valida JWT e preenche `EmpresaContext`.
- `CorsConfig`: CORS global permissivo.
- `RedisConfig`: JSON para valores e strings para chaves.
- `Config`: `ObjectMapper` e `RestTemplate` com connect/read timeout configuráveis.
- `WebConfig`: `/files/**` sobre filesystem local.
- `TerminalWebSocketConfig` e `WebSocketConfig`: WebSocket nativo e STOMP.

## Eventos e jobs

- Estoque: `OrderReservadaEvent`, `OrderPaidEvent`, `OrderNotCompletedEvent`, `CompraCanceladaEvent` → `EstoqueOrderListener`.
- Cobrança: `MercadoPagoCobrancaEvent` → `MercadoPagoCobrancaService`.
- Usuário: `UserCreatedEvent` → `UserListener @Async`.
- Socket: `PaymentEvent` → handlers nativo/STOMP.
- Jobs: `PaymentWorker` e dois métodos do `EmailWorker` a cada 2 s, com filas processing/retry/DLQ; `TerminalService.verificarTerminais` a cada 60 s.

## Testes existentes

Há 65 testes de contexto, repositories, services e WebSocket para hierarquia, onboarding, tenant administrativo, conta/terminal Mercado Pago, criação Point, assinatura e duplicidade de webhook, ordenação/mapeamento de status, terminal correto, desconexão, worker/DLQ, simulações, carrinho, usuário ativo e estoque. Não há cobertura HTTP ampla de segurança, concorrência real com MySQL/Redis, integração financeira real, arquivos, recuperação de senha ou aplicação real das migrations MySQL.
