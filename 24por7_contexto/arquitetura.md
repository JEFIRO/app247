# Arquitetura

Voltar para [[00-index]]. Persistência em [[banco-de-dados]], segurança em [[autenticacao]], pagamentos em [[mercado-pago]] e comunicação em [[websocket]].

## Organização de packages

O código está sob `com.jefiro.app247`:

- `domain.model`: entidades JPA (`Empresa`, `Condominio`, `Produto`, `Carrinho`, `Item`, `Order`, `Pagamento`, `MercadoPagoConta`, `WebhookEvent` e `GrupoTributario`);
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
| Catálogo | `ProdutoController`, `ProdutoService` | CRUD parcial, paginação, sincronização por `updateAt`, destaques e upload JPG/JPEG. |
| Compra | `CarrinhoService`, `OrderService` | Materializa itens a partir de produtos, calcula subtotal e cria pedido. |
| Checkout temporário | `CheckoutSessionService`, repository Redis | Sessão de 15 minutos, consulta de carrinho, vínculo de usuário e QR Code. |
| Pagamento Point | `PagamentoService`, `MercadoPagoCobrancaService` | Cria order local, dispara evento interno e chama `/v1/orders` do Mercado Pago. |
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

`OnboardingController` entrega `CadastroCompletoRequest` a `OnboardingService.executar`, método transacional. A empresa é persistida; o gestor é associado a ela, recebe papel `ADMIN` e senha BCrypt; endereço e condomínio são persistidos com `empresa_id`; o gestor recebe o condomínio inicial; e o terminal é persistido apenas com `condominio_id`. Uma falha propaga a exceção e reverte a transação relacional.

O JSON novo usa `gestor`; Jackson também aceita o nome legado `user`. A criação posterior de condomínios e terminais pertence aos services específicos.

### Carrinho e pedido

Ao criar um carrinho, o terminal determina condomínio e empresa. Cada `ItemRequest` busca produto nessa empresa, copia código, nome, preço, peso e foto e nasce como `VALIDATED`. Quantidade não positiva e produto duplicado são rejeitados. O subtotal é preço vezes quantidade e os itens são persistidos por cascade.

`createOrder` exige carrinho `OPEN`, impede segunda Order por consulta e constraint, muda o carrinho para `READY_FOR_PAYMENT`, cria a Order e publica reserva síncrona de estoque. O endpoint não fornece usuário, portanto o pedido dessa rota nasce sem usuário.

### Processamento assíncrono

O agendamento é habilitado em `App247Application`. A cada dois segundos, workers consomem as filas Redis de webhook e e-mail. Em erro, o item é recolocado à esquerda da mesma fila, sem contador, atraso progressivo ou dead-letter queue. A cada minuto, todos os terminais são percorridos para marcar como `OFFLINE` os que não enviaram ping recente.

## Configurações transversais

- CORS aceita qualquer origem, método e header, com credenciais habilitadas.
- `ObjectMapper` é bean compartilhado; `RestTemplate` possui connect/read timeout configuráveis. Ainda não há interceptors ou clients externos dedicados.
- uploads JPG/JPEG são gravados em `uploads/`, com nome baseado em hash parcial, timestamp e extensão; `/files/**` serve esse diretório.
- `@EnableAsync` ativa listeners assíncronos, especialmente e-mail e publicação STOMP.
- OpenAPI/Swagger é fornecido por Springdoc e a raiz redireciona para a UI.

## Limitações arquiteturais verificadas

- O isolamento foi imposto em empresa, condomínio, terminal, produto e estoque, mas ainda não é transversal a usuário, carrinho e Order; veja [[autenticacao]].
- `CondominioRepository` e `TerminalRepository` usam IDs `String`. `EnderecoRepository` ainda declara `Long` para um ID `String`, fora do fluxo refatorado.
- Há dois canais de pagamento WebSocket em paralelo, mas o fluxo ativo não publica seu evento.
- Exceções são frequentemente encapsuladas em `RuntimeException`, o que pode ocultar o tipo tratado pelo `RestExceptionHandler`.
- A deduplicação de webhook expira após 24 horas e não mantém histórico permanente.
- O worker de webhook ainda não possui backoff ou dead-letter queue.
