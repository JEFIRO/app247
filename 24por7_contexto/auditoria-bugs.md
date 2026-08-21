# Auditoria de bugs e riscos

Voltar para [[00-index]]. Recomendações priorizadas em [[melhorias]].

## Critérios

- **BUG CONFIRMADO**: o caminho executável permite diretamente o comportamento descrito.
- **RISCO / BUG POTENCIAL**: depende de dados, concorrência, ordem de mensagens ou ambiente não reproduzido nesta auditoria.
- O status abaixo reflete a implementação de 15 de agosto de 2026. Itens adiados ou parciais permanecem no histórico.

## BUG-001 — Usuários e dados pessoais acessíveis/alteráveis sem autenticação

### Classificação

`BUG CONFIRMADO` — severidade `CRÍTICO` — status `ADIADO`.

Motivo: risco aceito temporariamente durante desenvolvimento/testes. Ação futura obrigatória: autenticar e autorizar as rotas antes de produção. A resposta de usuário deixou de expor a entidade JPA, mas o acesso HTTP continua público por decisão explícita.

### Local

`SecurityConfig.securityFilterChain`; `UserController.getUser`, `update`, `salvar`, `getOrdersByUser`; `UserService.getUser`, `atualizarUsuario`, `salvarFoto`. Arquivos `infra/security/SecurityConfig.java`, `infra/controller/UserController.java`, `infra/service/UserService.java`.

### Problema, cenário e impacto

A regra residual é `permitAll` e somente três rotas de recuperação são mencionadas explicitamente. Logo `GET /user/{id}`, `PUT /user/{id}`, upload de foto, alteração de senha e histórico de orders são públicos. Busca e alteração usam ID global, sem principal/empresa. Quem obtiver um UUID pode ler PII, alterar nome/email/ativo ou substituir foto de usuário de outra empresa.

### Correção recomendada

Exigir JWT, derivar o usuário do principal para operações pessoais e criar rotas administrativas tenant-aware separadas. Nunca retornar a entidade `User`.

## BUG-002 — Cobrança, pedido e reserva de estoque podem ser iniciados anonimamente

### Classificação

`BUG CONFIRMADO` — severidade `CRÍTICO` — status `ADIADO`.

Motivo: rotas mutáveis legadas permanecem abertas para testes. Foram preservadas validações tenant quando existe `EmpresaContext`, e Order rejeita usuário/carrinho de empresas diferentes. Autenticação do terminal e troca dos `GET` mutáveis continuam pré-produção.

### Local

`SecurityConfig`; `OrderController.order`; `PagamentoController.getPagamento`; `OrderService.createOrder/criarCobranca`; `PagamentoService.gerarCobranca`.

### Problema, cenário e impacto

`/order/**` e `/pagamento/**` caem em `permitAll`. Um chamador com ID de carrinho pode fechá-lo, reservar estoque e disparar uma order Point real com a credencial da empresa. As operações mutáveis também usam `GET`, facilitando acionamento acidental por cache, prefetch ou crawler.

### Correção recomendada

Definir autenticação própria do terminal/checkout, autorização sobre o carrinho e métodos `POST`. Manter aliases somente durante migração controlada.

## BUG-003 — Credenciais literais e fallback sensível versionados

### Classificação

`BUG CONFIRMADO` — severidade `CRÍTICO` — status `PARCIALMENTE RESOLVIDO`.

Solução local: valores literais foram substituídos por `JWT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `MP_CLIENT_ID`, `MP_CLIENT_SECRET`, `MP_OAUTH_REDIRECT_URL`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` e demais variáveis de ambiente; o profile de teste usa segredos exclusivamente locais. A auditoria Point encontrou e removeu os últimos literais OAuth/SMTP ainda presentes no arquivo base, que divergiam da documentação. Pendente: rotacionar as credenciais que já estiveram no histórico, ação externa ao código.

### Local

`src/main/resources/application.properties` e `application-prod.properties`.

### Problema, cenário e impacto

Há valores literais para SMTP e banco de produção, além de configuração JWT com fallback não vazio. Os valores não são reproduzidos nesta documentação. Qualquer pessoa com acesso ao repositório/histórico pode obter credenciais; um fallback JWT conhecido permite forjar tokens caso a variável não seja definida.

### Correção recomendada

Rotacionar imediatamente os segredos, removê-los do histórico conforme processo seguro, exigir variáveis sem fallback e falhar no startup quando ausentes.

## BUG-004 — Migrations históricas alteradas e versões removidas/reutilizadas

### Classificação

`RISCO / BUG POTENCIAL` — severidade `CRÍTICO` — status `ABERTO`.

Dependência externa: é necessário comparar o `flyway_schema_history` de cada ambiente. Nenhuma migration histórica foi alterada nesta implementação; foram criadas somente V21, V22 e V23.

### Local

`V6__create_table_order.sql`, `V7__create_table_pagamento.sql`, `V17__alter_table_order.sql`; status Git mostra migrations V17/V18 antigas removidas e novas V18–V20.

### Problema, cenário e impacto

V6 e V7 diferem do conteúdo versionado no Git. Arquivos históricos de V17/V18 foram removidos/substituídos. Em banco que já registrou checksums antigos, `flyway validate/migrate` pode falhar; se validação estiver desabilitada, ambientes podem terminar com schemas diferentes para a mesma versão.

### Correção recomendada

Restaurar migrations aplicadas exatamente, criar somente versões novas e comparar `flyway_schema_history` de cada ambiente antes do deploy.

## BUG-005 — Entidades JPA e migrations não descrevem o mesmo schema

### Classificação

`BUG CONFIRMADO` no repositório; impacto em runtime é `RISCO` dependente do schema — severidade `ALTO` — status `PARCIALMENTE RESOLVIDO`.

Solução: V21–V23 adicionam ordenação de webhook, snapshot de unidade e alinham `origin_request`, `transaction_id`, `installments`, comprimentos e nulabilidade; `GrupoTributario` e `EnderecoRepository` foram alinhados. Produção passou a usar `ddl-auto=validate`. Pendente: validar V1–V23 em uma cópia MySQL e resolver o histórico descrito no BUG-004.

### Local

`Order.originRequest` sem coluna em V6; `Pagamento.transactionId` versus `id_transaction`; `Pagamento.installments` ausente em V7; `WebhookEvent` sem `empresa` enquanto V8 exige `empresa_id`; `GrupoTributario.aliquotaConfins` versus `aliquota_cofins`; ID JPA de 36 versus V14 com 25; `EnderecoRepository<Long>` para entidade `String`.

### Problema, cenário e impacto

Flyway sozinho não cria todas as colunas usadas pelo JPA. O projeto depende implicitamente de `ddl-auto=update`; um ambiente gerenciado apenas por migrations pode falhar em inserts/queries ou persistir dados em coluna diferente.

### Correção recomendada

Criar migrations corretivas novas, alinhar mappings e mudar produção para `ddl-auto=validate` após saneamento.

## BUG-006 — Usuário inativo continua autenticável

### Classificação

`BUG CONFIRMADO` — severidade `ALTO` — status `RESOLVIDO`.

Solução: `User.isEnabled()` agora retorna `Boolean.TRUE.equals(ativo)`. Testes: `UserTest` cobre usuários ativo e inativo.

### Local

`User.isEnabled`, arquivo `domain/model/auth/User.java`.

### Problema, cenário e impacto

O método retorna `UserDetails.super.isEnabled()`, que é verdadeiro, e ignora `User.ativo`. Desativar um usuário no banco não impede login nem uso de JWT.

### Correção recomendada

Retornar `Boolean.TRUE.equals(ativo)` e testar login/filtro para conta desativada.

## BUG-007 — Ordem temporal de webhooks não é validada

### Classificação

`RISCO / BUG POTENCIAL` — severidade `ALTO` — status `RESOLVIDO`.

Solução: `Order` persiste `mpEventVersion/mpEventDate`; a busca usa lock pessimista; versões antigas e transições inválidas são ignoradas por máquina de estados. V21 cria colunas e índice. `PagamentoServiceTest` cobre versão antiga e regressão com versão maior.

### Local

`MercadoPagoWebhookController.receive`; `PagamentoService.atualizarPagamento`; `EstoqueService.confirmarVenda/liberar`.

### Problema, cenário e impacto

A versão é usada apenas na chave Redis; não é persistida/comparada com a última versão da Order. Exemplo: `processed` é aplicado e confirma venda; depois um `failed` antigo pode regredir Order/Pagamento e liberar estoque. No sentido inverso, `failed` libera a reserva e um `processed` posterior registra VENDA com delta zero, deixando estoque recomposto apesar da venda.

### Correção recomendada

Persistir versão/data do evento, definir máquina de estados monotônica e fazer transição + estoque atomicamente apenas quando o evento for mais novo e válido.

## BUG-008 — Cart e Order não chegam a estado final local após pagamento

### Classificação

`BUG CONFIRMADO` — severidade `ALTO` — status `RESOLVIDO`.

Solução: aprovação preenche `Pagamento.paidAt`, `Order.paidAt` e `Carrinho.PAID`; falha, cancelamento, expiração e reembolso encerram o carrinho. Persistência e evento de estoque ocorrem na mesma transação.

### Local

`PagamentoService.atualizarPagamento`, caso `PROCESSED`; `Order` e `CarrinhoStatus`.

### Problema, cenário e impacto

Em aprovação, `Pagamento.paidAt` e status são atualizados, mas `Order.paidAt` não é preenchido e o carrinho permanece `PAYMENT_PENDING`, apesar de existir `CarrinhoStatus.PAID`. Consultas e clientes podem considerar a compra ainda pendente.

### Correção recomendada

Definir invariantes da transição aprovada e atualizar Pagamento, Order e Carrinho na mesma transação, com teste de webhook duplicado.

## BUG-009 — Respostas com entidades JPA possuem ciclos e dados excessivos

### Classificação

`RISCO / BUG POTENCIAL` — severidade `ALTO` — status `RESOLVIDO` para os endpoints identificados.

Solução: User, Order e Produto passaram a retornar `UserResponseDTO`, `OrderDetailResponse` e `ProdutoResponse`; carrinho já usava `CarrinhoResponseDTO`. Nenhuma senha ou relação reversa é serializada nesses contratos.

### Local

`OrderController`, `ProdutoController`, `UserController`; relações `Order ↔ Pagamento`, `Carrinho ↔ Item`, `User ↔ Order`; anotações Lombok `@Data/@ToString`.

### Problema, cenário e impacto

Controllers devolvem entidades. Uma Order pode serializar Pagamento que aponta de volta para Order; carrinho e itens também são bidirecionais. Isso pode causar recursão/erro de serialização e expor senha, relações e campos internos no endpoint de usuário.

### Correção recomendada

Usar DTOs explícitos, `@JsonIgnore` apenas como contenção e remover `toString/equals` automáticos de relações lazy.

## BUG-010 — Endpoints de sandbox Mercado Pago estão públicos e compilados em produção

### Classificação

`BUG CONFIRMADO` — severidade `ALTO` — status `MITIGADO`.

Solução: `TestarWebhookMPController` depende de `app.test-endpoints.enabled`; permanece habilitado por padrão para desenvolvimento, conforme decisão da tarefa, e é desabilitado em `application-prod.properties`. A rota continua pública quando habilitada.

### Local

`TestarWebhookMPController`; `TestarWebhookMP`; `SecurityConfig`.

### Problema, cenário e impacto

Onze endpoints públicos criam, listam, simulam e cancelam orders na conta de teste. `/teste-real` aceita IDs arbitrários de carrinho e usuário e usa um caminho de Order sem validação tenant consistente nem reserva.

### Correção recomendada

Condicionar beans a perfil de teste, não empacotar em produção e exigir autenticação administrativa adicional quando habilitados.

## BUG-011 — WebSockets permitem personificação de terminal

### Classificação

`BUG CONFIRMADO` — severidade `ALTO` — status `ADIADO`.

Motivo: autenticação de terminais/WebSocket exige contrato de provisionamento dos clientes. Permanece pendência obrigatória de pré-produção; logs não usam mais `System.out` nem reproduzem payload completo.

### Local

`TerminalWebSocketConfig`, `WebSocketConfig`, `TerminalWebSocketHandler`, `PaymentWebSocketHandler`, `TerminalService.updateStatus`.

### Problema, cenário e impacto

Handshake aceita qualquer origem e não autentica. O cliente escolhe o terminal pelo payload/URL; `updateStatus` faz `findById` global. Um cliente pode atualizar heartbeat/status de qualquer terminal conhecido ou ocupar a sessão de pagamento de outro terminal.

### Correção recomendada

Autenticar handshake, vincular principal ao terminal provisionado e rejeitar IDs diferentes da identidade da conexão.

## BUG-012 — Worker pode entrar em retry infinito e perder mensagem em queda

### Classificação

`RISCO / BUG POTENCIAL` — severidade `ALTO` — status `PARCIALMENTE RESOLVIDO`.

Solução: workers usam fila intermediária, removem após sucesso, limitam a três tentativas e enviam mensagens permanentes para DLQ. Teste: `PaymentWorkerTest`. Pendente: recuperação automatizada segura de itens que permanecerem em `processing` após queda e operação multi-instância.

### Local

`PaymentWorker.processQueue`; `EmailWorker.processQueue/sender`.

### Problema, cenário e impacto

Mensagem inválida é recolocada sem contador/backoff e reaparece a cada dois segundos, gerando loop e logs. Como o worker usa `rightPop` antes de processar, queda da JVM entre pop e conclusão perde a mensagem definitivamente.

### Correção recomendada

Usar fila de processamento/ack, retry limitado com backoff e dead-letter queue monitorada.

## BUG-013 — Recuperação de senha revela existência e perde status de domínio

### Classificação

`BUG CONFIRMADO` — severidade `MÉDIO` — status `RESOLVIDO`.

Solução: CPF inexistente mantém resposta neutra e não cria desafio; exceções de código/token deixaram de ser embrulhadas e chegam ao handler com os status previstos.

### Local

`UserService.recoveryPassword`, `verificarCode`, `novaSenha`, `alterarSenha`.

### Problema, cenário e impacto

Apesar da resposta neutra do controller, CPF inexistente lança erro. Além disso, `catch (Exception)` embrulha exceções como código expirado/inválido em `RuntimeException`, impedindo o handler de responder `410/400` como previsto.

### Correção recomendada

Resposta indistinguível para CPF existente/inexistente, preservar exceções conhecidas e aplicar rate limit/tentativas.

## BUG-014 — Validação de e-mail não altera `emailVerificado`

### Classificação

`BUG CONFIRMADO` — severidade `MÉDIO` — status `RESOLVIDO`.

Solução: após validar o desafio do e-mail, `UserService` marca `emailVerificado` e atualiza o usuário transacionalmente.

### Local

`UserService.sendCode/verificarCode(ValidateEmailRequest)` e `User.emailVerificado`.

### Problema e impacto

O código é validado e removido do Redis, mas nenhum usuário é buscado/atualizado. O campo permanece falso e não representa o resultado do fluxo.

### Correção recomendada

Associar desafio a usuário/ação, atualizar o campo transacionalmente e impedir que email arbitrário valide outra conta.

## BUG-015 — URLs de deep link de checkout são divergentes

### Classificação

`BUG CONFIRMADO` — severidade `MÉDIO` — status `RESOLVIDO`.

Solução: texto e QR agora usam `app247://session/{id}`.

### Local

`CheckoutSessionResponseDTO` usa `app24por7://session/`; `CheckoutSessionService.gerarQRCode` usa `app247://session/`.

### Impacto e correção

Texto e QR apontam para esquemas diferentes; um dos clientes pode não abrir. Definir um esquema único configurável e testar contrato.

## BUG-016 — Sessão permite associação de usuário arbitrário

### Classificação

`BUG CONFIRMADO` — severidade `MÉDIO` — status `PARCIALMENTE RESOLVIDO`.

Solução: vínculo passou a ser idempotente e não aceita substituir usuário já associado. Pendente: derivar o primeiro usuário do principal autenticado; impossível enquanto a rota permanecer pública por decisão temporária.

### Local

`GET /checkout`; `CheckoutSessionService.setUser`.

### Problema

Endpoint público recebe `id` e `session`, não valida usuário, empresa, carrinho nem estado, e renova o TTL ao salvar. Quem conhece o UUID pode sobrescrever `userId` da sessão.

### Correção recomendada

Usar usuário autenticado, operação idempotente `POST/PUT`, validar tenant/estado e impedir substituição posterior.

## BUG-017 — Upload público valida somente a extensão e devolve host fixo

### Classificação

`BUG CONFIRMADO` — severidade `MÉDIO` — status `PARCIALMENTE RESOLVIDO`.

Solução: o conteúdo é decodificado como imagem e a resposta usa URL relativa. Pendente: autenticação, limite de dimensões e armazenamento compartilhado.

### Local

`FileController.upload`; `FileStorageService.salvarArquivo`; `WebConfig`.

### Problema

Qualquer cliente pode gravar até 30 MB; conteúdo não é decodificado como imagem, apenas extensão é conferida. A resposta sempre usa `http://localhost:8080`, incorreto fora do desenvolvimento. Arquivos são locais à instância e públicos.

### Correção recomendada

Autenticar, validar MIME e conteúdo, limitar dimensões, gerar URL relativa/configurada e definir armazenamento compatível com múltiplas réplicas.

## BUG-018 — Tokens Mercado Pago expiram sem renovação

### Classificação

`RISCO / BUG POTENCIAL` — severidade `MÉDIO` — status `PARCIALMENTE RESOLVIDO`.

Solução: expiração agora produz erro explícito pedindo nova autorização. Refresh automático não foi inventado sem contrato externo completo e permanece pendente.

### Local

`MercadoPagoConta.dataExpiracao/refreshToken`; services Mercado Pago.

### Problema

Expiração e refresh token são persistidos, mas nenhuma chamada verifica prazo ou renova. Após expirar, listagem/vínculo/cobrança falham com erro externo.

### Correção recomendada

Implementar refresh conforme documentação oficial, com lock por empresa e atualização atômica, ou exigir reautorização com erro de domínio claro.

## BUG-019 — Clients HTTP externos não possuem timeout configurado

### Classificação

`RISCO / BUG POTENCIAL` — severidade `MÉDIO` — status `RESOLVIDO`.

Solução: o `RestTemplate` compartilhado usa connect/read timeout configuráveis e as integrações principais lançam `ExternalServiceException`, traduzida para HTTP 502 sem corpo sensível do provedor.

### Local

`Config.restTemplate`; OAuth, cobrança, consulta e terminais Mercado Pago.

### Problema

`new RestTemplate()` usa configuração padrão sem timeouts explícitos. Lentidão externa pode reter threads HTTP, transações e locks de estoque.

### Correção recomendada

Configurar connect/read timeout e política de retry apenas para operações idempotentes.

## BUG-020 — Webhook com status futuro é descartado definitivamente

### Classificação

`BUG CONFIRMADO` para o comportamento; impacto futuro é `RISCO` — severidade `MÉDIO` — status `RESOLVIDO`.

Solução: status desconhecido lança `UnknownExternalStatusException`; o worker aplica retry limitado e preserva o payload na DLQ para análise/reprocessamento.

### Local

`PagamentoService.atualizarPagamento`, linhas do `OrderStatus.findByValue == null`.

### Problema

Status desconhecido apenas imprime mensagem e retorna. O worker considera processamento concluído e não preserva payload para reprocessamento, então um status novo da API é perdido.

### Correção recomendada

Persistir evento bruto/auditoria, marcar estado desconhecido e alertar; não mapear automaticamente para outro status.

## BUG-021 — Order local de teste pode misturar empresas

### Classificação

`BUG CONFIRMADO` — severidade `ALTO` — status `RESOLVIDO` para mistura de tenant; risco operacional do simulador está mitigado pelo BUG-010.

Solução: `createOrderTest` rejeita usuário e carrinho de empresas diferentes e não substitui mais a empresa derivada do carrinho.

### Local

`TestarWebhookMPController.testarComOrderReal`; `TestarWebhookMP.testarComOrderReal`; `OrderService.createOrderTest`.

### Problema

O endpoint público aceita carrinho e usuário. `createOrderTest` constrói a Order do carrinho e depois substitui `order.empresa` pela empresa do usuário sem conferir igualdade. Também não fecha carrinho nem reserva estoque.

### Correção recomendada

Remover o fluxo do artefato produtivo. Em testes internos, validar tenant e reutilizar o mesmo caso de uso de produção com doubles da API externa.

## BUG-022 — Resposta de ativação pode lançar NPE para terminal incompleto

### Classificação

`RISCO / BUG POTENCIAL` — severidade `BAIXO` — status `ABERTO`.

### Local

`TerminalActivationResponse(Terminal)` chama `terminal.getStatus().toString()` e acessa condomínio sem checks.

### Problema

Schema/mappings permitem alguns campos nulos em estados legados. Terminal sem status ou associação inconsistente causa `500` no endpoint público de ativação.

### Correção recomendada

Reforçar `NOT NULL`, sanear legado e mapear respostas defensivamente.

## BUG-023 — Resultado Point não era comunicado ao Terminal

### Classificação

`BUG CONFIRMADO` — severidade `ALTO` — status `RESOLVIDO`.

### Local

`PagamentoService.atualizarPagamento`, `PaymentEvent`, `PaymentWebSocketHandler`, `PaymentSocketService`.

### Problema

O webhook atualizava Pagamento, Order e estoque, mas não publicava `PaymentEvent`. O Terminal permanecia aguardando mesmo após aprovação, recusa, cancelamento ou expiração.

### Solução

Cada transição aplicada publica `PaymentEvent`; os listeners executam após commit e enviam apenas à sessão identificada por `Order -> Carrinho -> Terminal`. Terminal offline não interrompe a transação e recupera o estado por HTTP.

### Testes

`PagamentoServiceTest.aprovacaoPublicaStatusParaTerminalDaOrder` e `PaymentWebSocketHandlerTest` cobrem terminal correto, terminal diferente e desconexão.

## BUG-024 — Início Point não retornava estado e clique repetido gerava erro

### Classificação

`BUG CONFIRMADO` — severidade `ALTO` — status `RESOLVIDO`.

### Local

`PagamentoController`, `PagamentoService.gerarCobranca`, `OrderService.criarCobranca`, `CarrinhoRepository`.

### Problema

O endpoint retornava apenas `true`; uma cobrança existente gerava conflito e não havia serialização local de cliques concorrentes nem consulta mínima para reconexão.

### Solução

`POST /pagamento/terminal/{carrinhoId}` retorna `PointPaymentResponse`; o carrinho é bloqueado pessimisticamente, cobrança existente é reutilizada e `X-Idempotency-Key` continua sendo o ID local. `GET /order/{orderId}/status?terminalId=...` permite ressincronização sem nova cobrança.

## BUG-025 — Carrinho inconsistente podia chegar à cobrança Point

### Classificação

`BUG CONFIRMADO` — severidade `ALTO` — status `RESOLVIDO`.

### Local

`CarrinhoRequest`, `ItemRequest`, `CarrinhoService.save`, `CarrinhoService.validarParaPagamento` e `OrderService.criarCobranca`.

### Problema

Chamadas sem itens podiam persistir carrinho com subtotal zero, e a fronteira de pagamento não recalculava o subtotal nem revalidava snapshots e tenant antes de reservar estoque e chamar o Mercado Pago.

### Solução

Bean Validation exige terminal, itens, produto e quantidade positiva. O service mantém validações para chamadas internas e, sob o lock do carrinho, recalcula o subtotal e valida item/produto/empresa/terminal antes de criar ou reutilizar a cobrança.

### Testes

`CarrinhoServiceTest.rejeitaCarrinhoVazioAntesDePersistir` e `rejeitaSubtotalDivergenteAntesDoPagamento`.
