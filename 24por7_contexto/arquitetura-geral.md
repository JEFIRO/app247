# Arquitetura geral atual

Voltar para [[00-index]]. Inventário em [[mapa-projeto]], contratos em [[api]] e riscos em [[auditoria-bugs]].

## Visão de execução

O módulo de promoções segue `Flutter → PromocaoService → PricingService → Product Sync → Terminal → Carrinho/Order → Mercado Pago`. `EstoqueCondominio` continua definindo disponibilidade. Eventos de catálogo separam a transação da notificação WebSocket `AFTER_COMMIT`, enquanto o sync incremental é a recuperação durável.

Inventário segue `Flutter → InventarioService → snapshot/lock do estoque → MovimentacaoEstoque + AuditLog`. Importação segue `Flutter → validação XLSX/preview → evento AFTER_COMMIT → processador assíncrono → transação por Produto`; nenhum dos dois cria uma segunda implementação de Produto ou estoque.

```text
Aplicativo / Terminal / Painel administrativo / Mercado Pago
                         |
                         v
               Controllers e WebSockets
                         |
                         v
                      Services
                  /       |       \
                 v        v        v
        Repositories   Redis   Integrações HTTP/SMTP
             |                     |
             v                     v
           MySQL              Mercado Pago / SMTP
```

É uma aplicação Spring Boot monolítica em camadas técnicas. Não há módulos Maven separados, portas de domínio ou clients externos abstraídos por interfaces. Controllers chamam services; services combinam regra de aplicação, repositories, Redis, SDK Mercado Pago, `RestTemplate`, filesystem e eventos Spring.

## Dependências externas

```text
Spring Boot
├── MySQL + JPA/Hibernate
├── Flyway
├── Redis
│   ├── sessões de checkout
│   ├── códigos temporários
│   ├── OAuth state
│   └── filas e deduplicação de webhook
├── Mercado Pago
│   ├── OAuth
│   ├── terminals/v1/list
│   ├── v1/orders
│   └── SDK para PIX/Checkout Pro não exposto
├── SMTP Gmail
├── Apache POI (modelo e leitura XLSX não confiável)
├── filesystem local uploads/
└── WebSocket nativo + STOMP
```

## Domínio e tenant

`Empresa` é a fronteira lógica do tenant:

```text
Empresa
├── Users
├── MercadoPagoConta (0..1)
├── Produtos
│   ├── ProdutoCodigoBarras
│   └── ProdutoFiscal
└── Condomínios
    ├── Terminais
    └── EstoqueCondominio
        └── Produto da mesma Empresa
```

Não há filtro Hibernate global. A autorização continua em services/repositories e `EmpresaContext`, enquanto o schema reforça as relações críticas com `empresa_id` controlado e FKs compostas. Terminal deriva o tenant exclusivamente por Condomínio; carrinho, venda, estoque, promoção e pagamento não aceitam combinações cross-tenant no banco.

## Fluxo de autenticação

```text
POST /auth/login
  -> UserService.login
  -> AuthenticationManager
  -> AutenticacaoService.loadUserByUsername(CPF)
  -> UserRepository.findByCpf
  -> BCrypt
  -> TokenService.generateToken
  -> JWT { subject=CPF, userId, empresaId }

Requisição posterior
  -> SecurityFilter
  -> extrai Authorization/Bearer
  -> TokenService.validate
  -> UserRepository.findByCpf
  -> SecurityContext
  -> EmpresaContext(ThreadLocal)
  -> Controller/Service
  -> finally EmpresaContext.clear()
```

Problemas observados: validade de 200 horas, ausência de issuer/audience/revogação, parser permissivo do header Bearer, token inválido propagado como exceção genérica e grande conjunto residual de endpoints públicos. Veja [[autenticacao]] e [[auditoria-bugs]].

## Fluxo de onboarding

```text
POST /onboarding
  -> OnboardingService @Transactional
  -> Empresa
  -> User gestor ADMIN + BCrypt
  -> Endereco + Condominio
  -> vínculo gestor-condomínio
  -> Terminal
```

A transação relacional reverte o conjunto em falha. O e-mail de boas-vindas é disparado por evento assíncrono durante o fluxo de criação do usuário; ele não participa atomicamente do banco.

## Fluxo real de compra Point

```text
POST /carrinho
  -> Terminal por ID
  -> empresa = terminal.condominio.empresa
  -> produtos validados nessa empresa
  -> CartItem mutável + subtotal preciso
  -> Carrinho OPEN

GET /order/finalizar ou POST /pagamento/terminal/{carrinho}
  -> valida itens, snapshots, subtotal positivo e tenant do carrinho
  -> Order única por carrinho
  -> OrderItem recebe snapshot imutável
  -> Carrinho READY_FOR_PAYMENT
  -> OrderReservadaEvent síncrono
  -> EstoqueService.reservar
       lock pessimista
       saldo -= quantidade, mesmo negativo
       movimento RESERVA idempotente

Início de cobrança
  -> lock pessimista do Carrinho
  -> Transação A: cria/reutiliza Order + PaymentAttempt PENDING
  -> Carrinho PAYMENT_PENDING
  -> commit local antes da integração
  -> credencial: Order.empresa -> MercadoPagoConta
  -> maquininha: Order.idTerminal -> Terminal.mercadoPagoTerminalId
  -> POST Mercado Pago /v1/orders fora da transação
  -> Transação B: IDs/status remoto na mesma PaymentAttempt
  -> response WAITING_PAYMENT para o Terminal

Webhook
  -> valida HMAC
  -> deduplica 24h no Redis
  -> mp_queue
  -> PaymentWorker move para mp_queue:processing
  -> PagamentoService @Transactional
  -> lock da Order + versão externa + máquina de estados
  -> localiza PaymentAttempt por provider + externalReference
  -> atualiza PaymentAttempt/Order/Carrinho e anexa PaymentEvent
  -> confirma processamento ou faz retry limitado/DLQ
  -> evento síncrono de estoque
       processed: VENDA delta zero
       failed/expired: LIBERACAO
       canceled/refunded: CANCELAMENTO
  -> PaymentEvent registrado para AFTER_COMMIT
  -> somente /payment-socket/{terminalId da Order}

Reconexão
  -> GET /order/{orderId}/status?terminalId={terminalId}
  -> lê estado persistido; não cria nova cobrança
```

Order possui `OrderItem` próprio e imutável; o histórico não depende mais de `CartItem` ou do cadastro atual do Produto. Uma Order possui várias `PaymentAttempt`, embora o fluxo atual ainda inicie apenas a primeira.

## Eventos e consistência transacional

- `OrderReservadaEvent`, `OrderPaidEvent`, `OrderNotCompletedEvent` e `CompraCanceladaEvent` são síncronos com `@EventListener`.
- Os métodos de estoque são transacionais e participam da transação publicadora quando chamados por proxy.
- A criação Point usa duas transações locais `REQUIRES_NEW`; a chamada HTTP ocorre entre elas e nunca dentro de callback `afterCompletion`.
- `UserCreatedEvent` é assíncrono e envia SMTP sem fila Redis.
- `PaymentEvent` é publicado pelo webhook quando o estado muda; listeners nativo/STOMP executam após commit.
- `ProdutoCatalogChangedEvent` é publicado por alterações globais do produto e por associação/desassociação no condomínio. `ProdutoCatalogNotificationService` o consome em `AFTER_COMMIT`, resolve os Terminais somente dos condomínios capturados no evento e envia `PRODUCT_SYNC_REQUIRED` pelo socket nativo já existente.
- `ImportacaoProdutoSolicitadaEvent` é publicado após a confirmação local e consumido de forma assíncrona somente depois do commit. Cada Produto fica isolado em `REQUIRES_NEW`, evitando uma transação de milhares de linhas.

## Redis e workers

| Chave/lista | Conteúdo | Expiração/consumo |
|---|---|---|
| `session:{id}` | `CheckoutSession` | 15 minutos |
| `oauth:mp:{state}` | `userId|empresaId` | 10 minutos |
| `recovery:{cpf}` | DTO com código | 15 minutos |
| `reset:{cpf}` | token UUID | 15 minutos |
| `email_validation_queue:{email}` | código | 15 minutos |
| `mp_webhook:{action}:{id}:{version}` | marcador | 24 horas |
| `mp_queue` | JSON de webhook | lista sem TTL |
| `recovery_queue` | DTO de e-mail | lista sem TTL |
| `email_validation_queue` | DTO de e-mail | lista sem TTL |

`PaymentWorker` e `EmailWorker` executam a cada dois segundos. A mensagem é movida atomicamente para uma lista `processing`, removida após sucesso e reprocessada no máximo três vezes. Falha permanente vai para uma DLQ e o contador expira em 24 horas. Queda após a movimentação não perde o payload, mas pode deixá-lo parado em `processing`; recuperação automática multi-instância permanece pendente.

## WebSocket

O sistema mantém dois modelos paralelos:

- WebSocket nativo em `/terminal-socket` e `/payment-socket/*`;
- STOMP em `/ws`, broker simples `/topic`.

Sessões são locais à JVM e não há autenticação no handshake. O heartbeat atualiza terminal globalmente pelo ID recebido na mensagem. Veja [[websocket]].

Desde 24 de agosto de 2026, `/terminal-socket` confirma cada heartbeat somente após `TerminalService.updateStatus` persistir e executar flush. O ACK contém o UUID, status e `lastPing` ISO-8601, permitindo que o cliente diferencie envio ao socket de atualização efetiva do banco.

O canal `/payment-socket/{terminalId}` também transporta invalidação de catálogo. Ele continua direcionado por Terminal e não carrega o produto completo. Após receber `PRODUCT_SYNC_REQUIRED`, o cliente consulta `GET /produtos/sync`, cuja resolução `Terminal -> Condominio` e os timestamps de `Produto`/`EstoqueCondominio` formam a fonte recuperável quando o socket esteve offline. Veja [[sincronizacao-produtos]].

## Persistência e evolução

Hibernate usa `ddl-auto=validate` em desenvolvimento integrado e produção. Flyway é a fonte única do schema; a baseline atual possui dez migrations e precisa de banco vazio. Veja [[database]] e [[database-er]].

## Tratamento de erros

`RestExceptionHandler` cobre exceções de usuário, senha, códigos, tokens, tenant, terminal, argumentos inválidos e conflitos de estado. Erros de integridade, IO e parte dos erros externos ainda não possuem contrato específico. Diversos services capturam `Exception` e relançam `RuntimeException`, removendo a classificação original.

## Observabilidade atual

- O heartbeat renova `terminal:online:{uuid}` no Redis e usa `lastPing` como fallback. A telemetria HTTP independente persiste projeção atual, histórico com retenção e alertas deduplicados. `TelemetryMaintenanceJobs` calcula OFFLINE pelo heartbeat; o Flutter usa DTOs filtrados por `EmpresaContext`. Veja [[telemetria]].
- Actuator está no classpath, sem métricas de negócio implementadas.
- Catálogo e sync possuem logs estruturados `[PRODUCT-CATALOG]` e `[PRODUCT-SYNC]`; ainda há saídas diretas em outras áreas legadas.
- Não existe correlation ID configurado.
- Cada `PaymentAttempt` persiste `external_reference` e `X-Idempotency-Key`, permitindo correlação e retries futuros sem reutilizar tentativas encerradas.
- Não há métricas para fila, retries, latência externa, webhooks ou estoque negativo.
