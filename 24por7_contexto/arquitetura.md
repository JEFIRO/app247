# Arquitetura

Voltar para [[00-index]]. Persistência em [[banco-de-dados]], segurança em [[autenticacao]], pagamentos em [[mercado-pago]] e comunicação em [[websocket]].

## Organização de packages

O código está sob `com.jefiro.app247`:

- `domain.model`: entidades JPA de tenant, catálogo/fiscal, estoque, promoção, carrinho/venda, `PaymentAttempt`/`PaymentEvent`, telemetria e auditoria;
- `domain.model.auth`: `User`, `Endereco` e `RoleUser`;
- `domain.model.terminal`: entidade `Terminal`;
- `domain.model.dto`: contratos REST, objetos de sessão e respostas;
- `infra.dto.onboarding`: request e response específicos do cadastro inicial da operação;
- `domain.model.dto.mercadopago`: corpos e respostas da API/webhook do Mercado Pago;
- `domain.model.enum_type`: estados e classificações persistidos como texto;
- `infra.controller`: endpoints HTTP;
- `infra.service`: regras de aplicação, integrações, workers e armazenamento de arquivos;
- `infra.repository`: Spring Data JPA e implementação Redis da sessão de checkout;
- `infra.security`: cadeia Spring Security e filtro JWT;
- `infra.config`: beans, CORS, Redis, arquivos e dois modelos WebSocket;
- `infra.websocket`: handlers WebSocket nativos;
- `infra.event`: eventos internos e listener de criação de usuário;
- `infra.exception`: exceções e handler REST.

Não há separação por interfaces de casos de uso ou adaptadores: controllers dependem diretamente de services, e services dependem diretamente de repositories, SDKs e `RestTemplate`.

## Componentes e responsabilidades

| Área | Componentes principais | Comportamento observado |
|---|---|---|
| Onboarding | `OnboardingController`, `OnboardingService` | Cria empresa, gestor `ADMIN`, primeiro condomínio e primeiro terminal em uma transação. |
| Gestão da empresa | `EmpresaService`, `CondominioService`, `TerminalService` | Opera recursos do tenant usando `EmpresaContext` e consultas compostas por empresa. |
| Catálogo | `ProdutoController`, `ProdutoService`, `ProdutoSyncService` | CRUD parcial, paginação, sync por Terminal usando timestamps de produto/disponibilidade, invalidação WebSocket após commit, destaques e upload JPG/JPEG. |
| Compra | `CarrinhoService`, `OrderService` | Materializa `CartItem`, calcula com precisão e congela `OrderItem` ao criar a venda. |
| Checkout temporário | `CheckoutSessionService`, repository Redis | Sessão de 15 minutos, consulta de carrinho, vínculo de usuário e QR Code. |
| Pagamento Point | `PagamentoService`, `PointPaymentPersistenceService`, `MercadoPagoCobrancaService` | Confirma a tentativa local, chama `/v1/orders` fora da transação e persiste a aceitação em nova transação. |
| Webhook | controller, validador HMAC, Redis `mp_queue`, `PaymentWorker` | Autentica, deduplica, enfileira, consulta dados faltantes e atualiza pedido/pagamento. |
| Identidade | `UserService`, `TokenService`, filtro de segurança | BCrypt, login por CPF, JWT e códigos temporários no Redis. |
| Terminal | `TerminalService`, WebSocket handler | Ativação por serial, heartbeat/status e marcação periódica como offline. |

## Endpoints REST encontrados

Os tipos exatos de request/response são definidos pelos DTOs. As rotas administrativas estão públicas temporariamente na camada HTTP, embora os services mantenham validações de tenant quando existe `EmpresaContext`.

| Base | Rotas observadas |
|---|---|
| `/auth` | login, login administrativo, códigos; `/register` exige gestor e força papel `USER` |
| `/user` | recuperação, validação e redefinição de senha; alteração de senha; pedidos por usuário; busca/atualização; foto |
| `/onboarding` | `POST` público; `POST /condominio` é alias legado temporário |
| `/empresas` | `GET /{empresaId}` e `PUT /{empresaId}`, limitados ao tenant |
| `/condominios` | criação, listagem, busca e atualização limitadas ao tenant |
| `/condominios/{id}/terminais` | criação e listagem após validar o condomínio no tenant |
| `/terminais` | busca e atualização individuais limitadas ao tenant |
| `/` | `GET` redireciona ao Swagger |
| `/produtos` | cadastro unitário multipart, lote, listagem, busca, atualização, sync e home |
| `/carrinho` | criação e busca por ID |
| `/checkout` | sessão, carrinho, QR Code e associação de usuário |
| `/order` | criação/finalização e busca; ambos recebem o parâmetro chamado `carrinho_id`, embora a busca o use como ID da order |
| `/pagamento` | `GET /terminal/{carrinho_id}` inicia cobrança Point |
| `/terminal` | `GET /serial/{serial}` retorna dados de ativação |
| `/mercado-pago` | início/callback OAuth e listagem tenant-aware de maquininhas; aliases legados permanecem em `/mp/oauth` |
| `/terminais/{id}/mercado-pago` | vínculo e desvínculo entre terminal interno e maquininha Point |
| `/webhook` | `POST /mercadopago` enfileira a notificação |
| `/files` | upload e exposição estática de arquivos em `/files/**` |
| `/api/testes/mercadopago` | cenários de simulação, listagem, cancelamento e limpeza de orders na API Mercado Pago |

## Fluxos internos relevantes

### Cadastro completo

`OnboardingController` entrega `CadastroCompletoRequest` a `OnboardingService.executar`, método transacional. A empresa é persistida; o gestor é associado a ela e recebe papel `ADMIN` e senha BCrypt; `CondominioService.salvarNovo` persiste explicitamente o endereço antes do condomínio (a relação não usa cascade); o gestor recebe o condomínio inicial; e o terminal é persistido apenas com `condominio_id`. Uma falha propaga a exceção e reverte toda a transação relacional.

O JSON novo usa `gestor`; Jackson também aceita o nome legado `user`. A criação posterior de condomínios e terminais pertence aos services específicos.

### Carrinho e pedido

Ao criar um carrinho, o Terminal determina condomínio e empresa. Cada `ItemRequest` busca produto/código nessa empresa, cria um `CartItem` com quantidade `BigDecimal` e nasce como `VALIDATED`. Quantidade não positiva e produto duplicado são rejeitados. Ao criar a Order, cada Produto referenciado pelo carrinho é recarregado integralmente por ID e empresa; então cada item é copiado para um `OrderItem` histórico. O snapshot exige SKU, nome, unidade, quantidade e valores monetários completos, e alterações posteriores no carrinho ou Produto não alteram a venda.

`createOrder` exige carrinho `OPEN`, impede segunda Order por consulta e constraint, muda o carrinho para `READY_FOR_PAYMENT`, cria a Order e publica reserva síncrona de estoque. O endpoint não fornece usuário, portanto o pedido dessa rota nasce sem usuário.

No início Point, `PointPaymentPersistenceService` cria a tentativa local em transação própria e bloqueia também a linha do Terminal, garantindo uma única Order intermediária por equipamento. `PointPaymentSubmissionService` faz a chamada externa fora dessa transação, serializa a mesma Order na instância e sempre reutiliza a chave idempotente persistida. `PaymentReconciliationService` é a única entrada para scheduler, startup e consultas HTTP; também recupera submissões `PENDING` cujo ID remoto ainda é desconhecido. Veja [[payment-recovery]].

### Processamento assíncrono

O agendamento é habilitado em `App247Application`. A cada dois segundos, workers consomem as filas Redis de webhook e e-mail. Em erro, o item é recolocado à esquerda da mesma fila, sem contador, atraso progressivo ou dead-letter queue. A cada minuto, todos os terminais são percorridos para marcar como `OFFLINE` os que não enviaram ping recente.

## Configurações transversais

- CORS aceita qualquer origem, método e header, com credenciais habilitadas.
- `ObjectMapper` é bean compartilhado; `RestTemplate` possui connect/read timeout configuráveis. Ainda não há interceptors ou clients externos dedicados.
- uploads JPG/JPEG são gravados em `uploads/`, com nome baseado em hash parcial, timestamp e extensão; `/files/**` serve esse diretório.
- `@EnableAsync` ativa listeners assíncronos, especialmente e-mail e publicação STOMP.
- OpenAPI/Swagger é fornecido por Springdoc e a raiz redireciona para a UI.

## Execução com Docker Compose

O `Dockerfile` usa build multi-stage com Maven e uma imagem final JRE 17. A aplicação roda como usuário sem privilégios, expõe `8080`, grava arquivos no volume `/app/uploads` e possui health check em `/actuator/health`.

O `docker-compose.yml` sobe API, MySQL 8.4 e Redis 8. A API aguarda os health checks dos dois serviços de dados; banco, Redis e uploads usam volumes separados. As portas de MySQL e Redis são publicadas somente em `127.0.0.1`, enquanto a porta HTTP pode ser configurada por `APP_PORT`.

O MySQL usa o volume `mysql_data_v2`, intencionalmente novo por causa da substituição da cadeia histórica pela baseline V1–V11. Um volume `mysql_data` criado pelo Compose anterior não é apagado automaticamente, mas também não pode ser reutilizado diretamente com a nova cadeia Flyway.

As credenciais e integrações são recebidas por variáveis locais. Para executar, copie `.env.example` para `.env`, substitua os valores `change-me` e use `docker compose up --build`. O arquivo `.env` é ignorado pelo Git e não entra no contexto de build.

## Limitações arquiteturais verificadas

- O schema impõe tenant por FKs compostas nos agregados críticos; a autorização HTTP continua dependendo de `EmpresaContext` e deve permanecer coberta nos services.
- `CondominioRepository`, `TerminalRepository` e `EnderecoRepository` usam IDs UUID textuais representados por `String`.
- Há dois canais de pagamento WebSocket em paralelo; ambos recebem o mesmo evento após commit, e o endpoint HTTP é o fallback de recuperação.
- Exceções são frequentemente encapsuladas em `RuntimeException`, o que pode ocultar o tipo tratado pelo `RestExceptionHandler`.
- A deduplicação operacional usa Redis e `webhook_event` mantém o inbox auditável com retenção configurada.
