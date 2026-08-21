# Arquitetura geral atual

Voltar para [[00-index]]. Inventário em [[mapa-projeto]], contratos em [[api]] e riscos em [[auditoria-bugs]].

## Visão de execução

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
└── Condomínios
    ├── Terminais
    └── EstoqueCondominio
        └── Produto da mesma Empresa
```

Não há filtro Hibernate global. O isolamento é aplicado manualmente em services/repositories. Empresa, condomínio, terminal, produto e estoque administrativos usam consultas compostas ou `EmpresaContext`. Carrinho valida o tenant quando o contexto existe e deriva a empresa do terminal quando não existe; usuários, Orders e sessões ainda possuem caminhos globais por ID.

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
  -> Item snapshots + subtotal
  -> Carrinho OPEN e Items por cascade

GET /order/finalizar ou POST /pagamento/terminal/{carrinho}
  -> valida itens, snapshots, subtotal positivo e tenant do carrinho
  -> Order única por carrinho
  -> Carrinho READY_FOR_PAYMENT
  -> OrderReservadaEvent síncrono
  -> EstoqueService.reservar
       lock pessimista
       saldo -= quantidade, mesmo negativo
       movimento RESERVA idempotente

Início de cobrança
  -> lock pessimista do Carrinho
  -> reutiliza Order/cobrança existente em clique repetido
  -> Carrinho PAYMENT_PENDING
  -> MercadoPagoCobrancaEvent síncrono
  -> credencial: Order.empresa -> MercadoPagoConta
  -> maquininha: Order.idTerminal -> Terminal.mercadoPagoTerminalId
  -> POST Mercado Pago /v1/orders
  -> Pagamento + dados MP na Order
  -> response WAITING_PAYMENT para o Terminal

Webhook
  -> valida HMAC
  -> deduplica 24h no Redis
  -> mp_queue
  -> PaymentWorker move para mp_queue:processing
  -> PagamentoService @Transactional
  -> lock da Order + versão externa + máquina de estados
  -> atualiza Order/Pagamento/Carrinho
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

Order não possui coleção própria de snapshots. O histórico depende dos itens do carrinho, mas não existem endpoints atuais para editar carrinho depois da criação e o status impede novo checkout pelo service.

## Eventos e consistência transacional

- `OrderReservadaEvent`, `OrderPaidEvent`, `OrderNotCompletedEvent` e `CompraCanceladaEvent` são síncronos com `@EventListener`.
- Os métodos de estoque são transacionais e participam da transação publicadora quando chamados por proxy.
- `MercadoPagoCobrancaEvent` também é síncrono; a chamada HTTP acontece dentro do fluxo transacional de cobrança.
- `UserCreatedEvent` é assíncrono e envia SMTP sem fila Redis.
- `PaymentEvent` é publicado pelo webhook quando o estado muda; listeners nativo/STOMP executam após commit.

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

## Persistência e evolução

Hibernate está configurado como `ddl-auto=update` em dev e produção, simultaneamente ao Flyway. Isso reduz a confiabilidade das migrations como representação exclusiva do schema. Há divergências entre JPA e SQL e modificações locais em migrations históricas; detalhes em [[banco-de-dados]] e [[auditoria-bugs]].

## Tratamento de erros

`RestExceptionHandler` cobre algumas exceções de usuário, senha, códigos, tokens, tenant e terminal. `IllegalArgumentException`, `IllegalStateException`, erros de integridade, IO e boa parte dos erros externos não possuem contrato específico. Diversos services capturam `Exception` e relançam `RuntimeException`, removendo a classificação original.

## Observabilidade atual

- Actuator está no classpath, sem métricas de negócio implementadas.
- Há `System.out.println`, `printStackTrace` e alguns logs SLF4J apenas no estoque.
- Não existe correlation ID configurado.
- O ID local da Order é `external_reference` e `X-Idempotency-Key`, o que permite correlação manual com Mercado Pago.
- Não há métricas para fila, retries, latência externa, webhooks ou estoque negativo.
