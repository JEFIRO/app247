# API HTTP e canais WebSocket

Voltar para [[00-index]]. Arquitetura em [[arquitetura-geral]], autenticação em [[autenticacao]] e riscos em [[auditoria-bugs]].

> Todas as rotas HTTP permanecem temporariamente abertas durante desenvolvimento/testes por uma única regra `anyRequest().permitAll()`. JWT e roles podem ser usados por regras internas quando enviados, mas não são exigidos pela camada HTTP nesta fase.

## Convenções observadas

- Não há prefixo global como `/api/v1`.
- JSON é o formato predominante; upload de produto/foto usa `multipart/form-data`.
- O header opcional de autenticação é `Authorization: Bearer {jwt}`; quando válido, ele fornece usuário e empresa aos fluxos que dependem de contexto.
- `Pageable` usa os parâmetros Spring `page`, `size` e `sort`.
- Erros tratados por `RestExceptionHandler` usam `RestErrorMessage`; validação MVC e exceções genéricas seguem a resposta padrão do Spring.
- User, Order, Produto e Carrinho usam DTOs explícitos. Endpoints legados restantes devem ser avaliados antes de ampliar seus contratos.
- Erros de integração externa são sanitizados como `502`; argumento inválido retorna `400`, ausência coberta pelo handler retorna `404` e conflito de estado retorna `409`.
- Status listados abaixo são os explicitamente produzidos pelo controller/handler. Exceções não tratadas normalmente resultam em `500`.

## Matriz de autenticação e autorização

Esta é a segurança configurada atualmente em `SecurityConfig`. A tabela registra a exigência HTTP efetiva, não a proteção desejada.

| Grupo de endpoint | Público | JWT | Papel exigido |
|---|---:|---:|---|
| `/auth/**` | sim | não | nenhum |
| `POST /onboarding`, `POST /condominio` | sim | não | nenhum |
| `/empresas/**` | sim | não | nenhum |
| `/condominios/**` | sim | não | nenhum |
| `/terminais/**` | sim | não | nenhum |
| `/mercado-pago/**`, `/mp/oauth/**` | sim | não | nenhum; callback ainda exige `state` válido na regra de negócio |
| `/produtos/**`, `/estoque/**` | sim | não | nenhum |
| `/user/recuperar`, `/user/validar`, `/user/redefinir-senha` | sim | não | nenhum |
| demais `/user/**` | sim pela regra residual | não | nenhum |
| `/carrinho/**`, `/checkout/**`, `/order/**`, `/pagamento/**` | sim pela regra residual | não | nenhum |
| `/files/**` | sim pela regra residual | não | nenhum |
| `/api/testes/mercadopago/**` | sim pela regra residual | não | nenhum |
| `/webhook/**`, ativação por serial e Swagger | sim | não | nenhum |

Segurança recomendada, não implementada: restringir operações de usuário ao principal, autenticar carrinho/pedido/pagamento conforme o cliente, proteger upload e testes externos, e autenticar handshakes WebSocket. Veja [[melhorias]].

# Autenticação

## POST /auth/login

- Controller/service: `AuthController.login` → `UserService.login`.
- Finalidade: autenticar CPF e senha e emitir JWT.
- Segurança: pública.
- Body `AuthDTO`: `cpf` e `senha`, ambos obrigatórios.
- Resposta `200`: `AuthResponse { token, user: UserResponseDTO }`.
- Erros: `400` para validação; `401` para credenciais inválidas; outros erros não tratados podem virar `500`.
- Efeitos: autenticação via `AuthenticationManager`; não altera `ultimoLogin`.
- Tenant: `empresaId` é incluído no token a partir do usuário.

## POST /auth/login/admin

- Controller/service: `AuthController.loginAdmin` → `UserService.loginAdmin`.
- Finalidade: login exclusivo de gestor.
- Segurança: pública; a role é verificada depois da senha.
- Body/saída: os mesmos de `/auth/login`.
- Resposta: `200`; `401` para credenciais inválidas; `403` se não for `ADMIN`/`GERENTE`.
- Efeito: emite o mesmo tipo de JWT.

## POST /auth/register

- Controller/service: `AuthController.create` → `UserService.cadastrar`.
- Finalidade: cadastrar usuário comum na empresa do gestor.
- Segurança HTTP: pública temporariamente; a operação depende de `EmpresaContext` e falha sem contexto válido, sem inventar empresa.
- Body `UserRequestDTO`: nome, sobrenome, email, senha, CPF, telefone, data de nascimento `dd/MM/yyyy`; `roleUser` é aceito no JSON, mas o service força `USER`.
- Resposta: `200` sem corpo.
- Erros: `400`, `401`, `403`, `409` para CPF duplicado; constraint de email pode resultar em erro não tratado.
- Efeitos: senha BCrypt, insert em `users` e `UserCreatedEvent` para e-mail assíncrono.
- Tenant: empresa vem de `EmpresaContext`; não é aceita no body.

## POST /auth/send-code

- Controller/service: `AuthController.sendCode` → `UserService.sendCode`.
- Finalidade: enviar código de validação de e-mail.
- Segurança: pública.
- Body `ValidateEmailRequest`: `email`, `nome`; `code` não precisa ser enviado.
- Resposta: `200` vazio.
- Efeitos: grava `email_validation_queue:{email}` com TTL de 15 minutos e enfileira na lista Redis homônima.
- Observação: não marca `User.emailVerificado`.

## POST /auth/validate-code

- Controller/service: `AuthController.validateCode` → `UserService.verificarCode(ValidateEmailRequest)`.
- Finalidade: comparar código de e-mail.
- Segurança: pública.
- Body: `email`, `nome`, `code`.
- Resposta: `200` vazio.
- Erros pretendidos: código expirado/inválido; atualmente são encapsulados em `RuntimeException` e tendem a `500`.
- Efeito: remove a chave Redis quando válido.

# Recuperação e usuários

## POST /user/recuperar

- Controller/service: `UserController.recuperar` → `UserService.recoveryPassword`.
- Segurança: pública.
- Body `PasswordRecovery`: `cpf` com 11 dígitos.
- Resposta: `202 { "message": "Se o CPF existir, enviamos instruções para recuperação." }`.
- Efeitos: cria código de seis dígitos, chave `recovery:{cpf}` por 15 minutos e item em `recovery_queue`.
- Risco: CPF inexistente gera exceção, contrariando a mensagem neutra.

## POST /user/validar

- Controller/service: `UserController.validar` → `UserService.verificarCode`.
- Segurança: pública.
- Body `ValidateCodeRequest`: `code`, `cpf`; email/nome são opcionais para esta operação.
- Resposta: `200 { "token": "uuid" }`.
- Efeitos: consome `recovery:{cpf}` e cria `reset:{cpf}` por 15 minutos.
- Erros pretendidos: `400` código inválido, `410` expirado; o wrapper genérico pode convertê-los em `500`.

## POST /user/redefinir-senha

- Controller/service: `UserController.redefinirSenha` → `UserService.novaSenha`.
- Segurança: pública.
- Body `ResetPasswordRequest`: `cpf`, `token`, `novaSenha`; o DTO não possui Bean Validation.
- Resposta: `200 true`.
- Efeitos: atualiza hash BCrypt e remove `reset:{cpf}`.
- Erros pretendidos: token inválido/expirado; podem virar `500` pelo encapsulamento.

## POST /user/alterar-senha

- Controller/service: `UserController.alterarSenha` → `UserService.alterarSenha`.
- Segurança real: pública pela regra residual.
- Body `ChangePasswordRequest`: `userId`, `oldPassword`, `newPassword`, sem validações declaradas.
- Resposta: `200 { "message": "Senha alterada com sucesso" }`.
- Efeitos: atualiza senha do ID informado se a senha antiga corresponder.
- Tenant: não valida principal nem empresa.

## GET /user/{userId}/orders

- Controller/service: `UserController.getOrdersByUser` → `UserService.getOrderByUser`.
- Segurança real: pública.
- Path: `userId`; query: `page`, `size`, `sort`.
- Resposta: `Page<OrderDTO>` com ID, status, valores e data.
- Tenant: consulta apenas por usuário, sem principal/empresa.

## GET /user/{userId}

- Controller/service: `UserController.getUser` → `UserService.getUser`.
- Segurança real: pública.
- Resposta: `UserResponseDTO`, sem senha e sem relações JPA.
- Erro: `404` se inexistente.
- Tenant: busca global por ID.

## POST /user/foto?id={userId}

- Controller/service: `UserController.salvar` → `UserService.salvarFoto`.
- Segurança real: pública.
- Entrada multipart: part `file`; query `id`.
- Resposta: `200 true`.
- Efeitos: grava JPG/JPEG em `uploads/` e altera `fotoPerfil` do ID informado.
- Tenant: não valida principal/empresa.

## PUT /user/{id}

- Controller/service: `UserController.update` → `UserService.atualizarUsuario`.
- Segurança real: pública.
- Body `UserUpdate`: nome, sobrenome, email, telefone, ativo e condominioId, todos opcionais.
- Resposta: `200` com mensagem.
- Efeitos: atualiza `users`; condomínio, quando informado, exige `EmpresaContext`, que não existe em chamada anônima.
- Tenant: sem condomínio no body, um ID arbitrário pode ser alterado globalmente.

# Onboarding, empresas e condomínios

## POST /onboarding e POST /condominio

- Controller/service: `OnboardingController.criar` → `OnboardingService.executar`.
- Segurança: pública; `/condominio` é alias legado.
- Body `CadastroCompletoRequest`: `gestor` (alias `user`), `empresa`, `condominio`, `terminal`.
- Resposta `201 OnboardingResponse`: IDs do gestor, empresa, condomínio e terminal.
- Efeitos: cria toda a hierarquia em uma transação; gestor recebe `ADMIN` e senha BCrypt.
- Erros: validação `400`, conflitos de CPF/CNPJ/email/serial conforme constraints; parte dos conflitos vira `500`.

## GET /empresas/{empresaId}

- Controller/service: `EmpresaController.buscar` → `EmpresaService.getEmpresaDoContexto`.
- Segurança HTTP: pública temporariamente; requer contexto de empresa para concluir a operação.
- Resposta `200 EmpresaResponse`; `403` se o ID não coincide com `EmpresaContext`.
- Tenant: comparação explícita com empresa autenticada.

## PUT /empresas/{empresaId}

- Controller/service: `EmpresaController.atualizar` → `EmpresaService.atualizar`.
- Segurança HTTP: pública temporariamente; requer contexto de empresa para concluir a operação.
- Body/saída: `EmpresaRequest` → `EmpresaResponse`.
- Efeito: atualiza dados cadastrais; tenant validado antes da gravação.

## POST /condominios

- Controller/service: `CondominioController.criar` → `CondominioService.criar`.
- Segurança HTTP: pública temporariamente; requer contexto de empresa para concluir a operação.
- Body `CondominioRequest`: nome, CNPJ e `EnderecoDTO` opcional.
- Resposta: `201 CondominioResponse`.
- Efeito: cria condomínio/endereço na empresa autenticada.

## GET /condominios

- Finalidade: listar condomínios da empresa autenticada.
- Controller/service: `CondominioController.listar` → `CondominioService.listar`.
- Segurança HTTP: pública temporariamente; requer contexto de empresa para concluir a operação.
- Resposta: `200 List<CondominioResponse>`.

## GET /condominios/{condominioId}

- Finalidade: buscar condomínio dentro do tenant.
- Segurança HTTP: pública temporariamente; requer contexto de empresa para concluir a operação.
- Resposta: `200 CondominioResponse`; `404` se ausente ou de outra empresa.

## PUT /condominios/{condominioId}

- Finalidade: atualizar condomínio/endereço no tenant.
- Segurança HTTP: pública temporariamente; requer contexto de empresa para concluir a operação.
- Body/saída: `CondominioRequest` → `CondominioResponse`.
- Efeitos: substitui o endereço por nova entidade quando enviado.

# Terminais

## GET /terminal/serial/{serial}

- Controller/service: `TerminalController.bySerial` → `TerminalService.getBySerial`.
- Segurança: pública.
- Resposta `TerminalActivationResponse`: IDs e nomes do terminal/condomínio, status e ativação.
- Erro: `404` se serial inexistente.
- Tenant: não requer contexto; destina-se à ativação física.

## POST /condominios/{condominioId}/terminais

- Segurança HTTP: pública temporariamente; requer contexto de empresa para concluir a operação.
- Body `TerminalRequest`: nome, serialNumber, macAddress, ipAddress.
- Resposta: `201 TerminalResponseDTO`.
- Efeito: cria terminal no condomínio validado contra `EmpresaContext`.

## GET /condominios/{condominioId}/terminais

- Segurança HTTP: pública temporariamente; requer contexto de empresa para concluir a operação.
- Resposta: lista de `TerminalResponseDTO` limitada ao condomínio/empresa.

## GET /terminais/{terminalId}

- Segurança HTTP: pública temporariamente; requer contexto de empresa para concluir a operação.
- Resposta: `TerminalResponseDTO`; `404` também oculta terminal de outro tenant.

## PUT /terminais/{terminalId}

- Segurança HTTP: pública temporariamente; requer contexto de empresa para concluir a operação.
- Body/saída: `TerminalRequest` → `TerminalResponseDTO`.
- Efeito: atualiza dados físicos sem alterar condomínio.

## PUT /terminais/{terminalId}/mercado-pago

- Controller/service: `TerminalController.vincularMercadoPago` → `MercadoPagoTerminalService.vincular`.
- Segurança HTTP: pública temporariamente; requer contexto de empresa para concluir a operação.
- Body: `{ "mercadoPagoTerminalId": "..." }`.
- Resposta: `200 TerminalResponseDTO`.
- Efeitos: consulta a conta Mercado Pago da empresa, valida a maquininha e grava vínculo único.
- Erros externos/negócio: conta ausente, maquininha ausente na conta, vínculo duplicado ou HTTP externo; normalmente `500`.

## DELETE /terminais/{terminalId}/mercado-pago

- Segurança HTTP: pública temporariamente; requer contexto de empresa para concluir a operação.
- Resposta: `204`.
- Efeito: remove vínculo após validar conta OAuth e tenant.

# Produtos

Os endpoints administrativos de `/produtos/**` estão públicos temporariamente na camada HTTP e derivam a empresa de `EmpresaContext`. A exceção é o sync do Terminal, que deriva empresa e condomínio da identidade física `uuidTerminal`.

## POST /produtos

- Controller/service: `ProdutoController.salvar` → `ProdutoService.salvar`.
- Entrada multipart: part JSON `data: CreateProductDTO` e `file` JPG/JPEG opcional.
- Resposta: `200 ProdutoResponse`.
- Efeitos: cria produto no catálogo e possivelmente arquivo local.
- Observação: `quantidade` legado é ignorado; estoque é criado separadamente.
- Conflito: código já usado por outro produto da empresa retorna `409` antes de armazenar imagem.

## POST /produtos/save-list

- Body: lista de `CreateProductDTO`.
- Resposta: `200 List<ProdutoResponse>`.
- Efeito: insert em lote no catálogo da empresa; lista vazia gera erro genérico.

## GET /produtos

- Query: paginação Spring.
- Resposta `PageResponse<ProdutoListagemDTO>`; `quantidade` permanece no contrato, sempre nulo.

## GET /produtos/{codigo}

- Resposta: `ProdutoResponse` buscado por código + empresa.
- Observação de roteamento: caminhos estáticos `/id`, `/sync` e `/home` competem com este template, embora o Spring priorize correspondências literais.

## GET /produtos/id?id={produtoId}

- Resposta: `ProdutoResponse` do tenant.

## PUT /produtos/{id}

- Entrada multipart igual ao cadastro.
- Resposta: `200 ProdutoResponse`.
- Efeitos: atualiza catálogo e `Produto.updateAt`; após commit notifica somente os Terminais dos condomínios com associação ativa.
- Conflito: tentativa de usar o código de outro produto da empresa retorna `409`.

## PATCH /produtos/{id}/disponibilidade

- Body: `{ "ativo": false }`.
- Resposta: `200 ProdutoResponse`.
- Efeito: ativa/desativa globalmente o produto. A desativação produz `REMOVE` no sync de todos os condomínios que ainda possuíam associação ativa.

## GET /produtos/sync?uuidTerminal={uuid}&lastSync={ISO_INSTANT opcional}

- Controller/service: `ProdutoController.sync` → `ProdutoSyncService.sincronizar`.
- Identidade: busca global do Terminal pelo UUID e deriva `Terminal -> Condominio`; não usa `EmpresaContext`.
- Sem `lastSync`: full sync dos produtos atualmente disponíveis (`EstoqueCondominio.ativo=true` e `Produto.status=true`).
- Com `lastSync`: consulta associações cujo `EstoqueCondominio.updatedAt` ou `Produto.updateAt` esteja em `(lastSync, syncAt]`.
- `lastSync` e `syncAt` usam `Instant` UTC; o cliente deve persistir exclusivamente o `syncAt` devolvido pelo backend.
- Resposta:

```json
{
  "syncAt": "2026-08-24T16:50:32.123Z",
  "fullSync": false,
  "changes": [
    { "productId": "123", "operation": "UPSERT", "produto": { "id": "123" } },
    { "productId": "456", "operation": "REMOVE", "produto": null }
  ]
}
```

`UPSERT` contém `ProdutoSyncItem`, composto pelos dados globais do produto e `quantidade` da associação. `REMOVE` é tombstone idempotente. Quantidade é informativa neste contrato: baixas de venda, entradas e ajustes não alteram o cursor de catálogo nem disparam `PRODUCT_SYNC_REQUIRED`.

`200` com `fullSync=true` e `changes=[]` é resposta válida quando o condomínio do Terminal não possui associação ativa com produto ativo. Produto existente apenas no catálogo da empresa não entra no resultado. O serviço registra `terminalUuid`, Terminal resolvido, condomínio, empresa, janela, modo, associações encontradas e mudanças, sem registrar credenciais.

## GET /produtos/home

- Resposta: `{ "destaques": [ProdutoResponse...] }` com até dez produtos recentes da empresa.

# Estoque

Todos estão públicos temporariamente na camada HTTP; condomínio e produto continuam sendo validados contra a empresa presente no contexto.

## GET /condominios/{condominioId}/estoque

- Service: `EstoqueService.listarCondominio`.
- Resposta: `List<EstoqueResponse { produtoId, produto, quantidade, ativo }>`.

## POST /condominios/{condominioId}/estoque

- Body `EstoqueRequest`: `produtoId`, `quantidade` decimal.
- Resposta: `200 EstoqueResponse`.
- Efeitos: cria ou reativa disponibilidade e movimento `ENTRADA`; aceita saldo inicial negativo; publica notificação de catálogo após commit apenas para o condomínio informado.
- Erros: produto já disponível ou tenant inválido; exceções de negócio não possuem handler específico.

## POST /condominios/{condominioId}/estoque/{produtoId}/entrada

- Body `QuantidadeEstoqueRequest`: quantidade e motivo opcional.
- Resposta: `200 EstoqueResponse`.
- Efeito: lock pessimista, soma e audita `ENTRADA`; valor negativo é rejeitado, zero é aceito.

## PUT /condominios/{condominioId}/estoque/{produtoId}

- Body: quantidade absoluta desejada e motivo.
- Efeito: lock pessimista e movimento `AJUSTE`; saldo negativo é permitido.

## DELETE /condominios/{condominioId}/estoque/{produtoId}

- Resposta: `200 EstoqueResponse` com `ativo=false`.
- Efeito: soft delete da disponibilidade, preservando a associação como tombstone; após commit notifica somente os Terminais daquele condomínio.
- Idempotência: repetir sobre associação já inativa não publica novo evento.

## GET /condominios/{condominioId}/estoque/movimentacoes

- Resposta: `List<MovimentacaoEstoqueResponse>` em ordem decrescente.
- Efeito: somente leitura; pode executar N+1 para relações lazy.

## GET /estoque/geral

- Resposta: `List<EstoqueResponse>` agregada por produto via `SUM` no banco.
- Observação: campo `ativo` é sempre `true` na projeção agregada.

# Carrinho e checkout temporário

## POST /carrinho

- Controller/service: `CarrinhoController.addCarinho` → `CarrinhoService.save`.
- Segurança real: pública.
- Body `CarrinhoRequest`: `terminalId` e lista `items [{ productId, quantity, receivedWeight }]`.
- Validação: `terminalId` obrigatório; lista não nula/não vazia; item e `productId` obrigatórios; quantidade inteira positiva.
- Resposta: `200 CarrinhoResponseDTO` com itens snapshot.
- Efeitos: valida terminal, força produtos à empresa dele, rejeita quantidade não positiva e produto duplicado, calcula subtotal e persiste itens por cascade.
- Erros: terminal/produto inexistente, lista/itens nulos e duplicidade; em geral `500` por falta de handlers.

## GET /carrinho/{id}

- Segurança real: pública.
- Resposta: `200 CarrinhoResponseDTO`; busca global pelo UUID, sem tenant.

## GET /checkout/carrinho?idCarrinho={id}

- Segurança real: pública.
- Efeito: cria sessão Redis de 15 minutos para o carrinho.
- Resposta `CheckoutSessionResponseDTO` com sessionId, carrinhoId, terminalId, URL `app24por7://session/{id}`, status e expiração.

## GET /checkout/session?idSession={id}

- Segurança: pública.
- Resposta: `CarrinhoResponseDTO` da sessão.
- Efeito colateral: imprime o ID da sessão no console.

## GET /checkout/qrcode?id={sessionId}

- Segurança: pública.
- Resposta: `200 image/png` contendo `app247://session/{id}`.
- Divergência: o DTO textual usa esquema `app24por7://`, enquanto o QR usa `app247://`.

## GET /checkout?id={userId}&session={sessionId}

- Segurança: pública.
- Finalidade: associar userId à sessão.
- Resposta: `200 true`.
- Efeito: renova o TTL para 15 minutos ao salvar; não valida existência, tenant ou autenticação do usuário.

# Pedidos e pagamentos

## GET /order/finalizar?carrinho_id={id}

- Controller/service: `OrderController.order` → `OrderService.createOrder`.
- Segurança real: pública.
- Resposta: `OrderDetailResponse` com resumo do pagamento e snapshot do carrinho.
- Efeitos: exige carrinho `OPEN`, muda para `READY_FOR_PAYMENT`, cria uma Order e publica reserva síncrona de estoque.
- Concorrência: service faz consulta prévia e V20 impõe `UNIQUE(id_carrinho)`.

## GET /order?carrinho_id={id}

- Segurança real: pública.
- Comportamento real: apesar do nome do query param, o valor é passado a `getOrder` como ID da Order, não do carrinho.
- Resposta: `OrderDetailResponse`; a consulta continua global enquanto não houver `EmpresaContext`.

## GET /pagamento/terminal/{carrinho_id}

- Controller/service: `PagamentoController.getPagamento` → `PagamentoService.gerarCobranca`.
- Segurança real: pública.
- Resposta: `200 true` quando a execução síncrona termina.
- Efeitos: reutiliza/cria Order, reserva estoque se necessário, marca carrinho `PAYMENT_PENDING`, chama Mercado Pago Point, cria Pagamento e atualiza Order.
- Dependências: terminal precisa de `mercadoPagoTerminalId` e empresa de `MercadoPagoConta`.
- Erros: resposta externa inválida gera conflito de estado; HTTP/timeout/indisponibilidade externos são sanitizados como `502`.

Este GET é um alias legado. Novas versões do Terminal devem usar o POST abaixo.

## POST /pagamento/terminal/{carrinho_id}

- Controller/service: `PagamentoController.iniciarPagamentoPoint` → `PagamentoService.gerarCobranca`.
- Segurança HTTP: pública temporariamente; tenant é derivado do carrinho/terminal e comparado com `EmpresaContext` quando presente.
- Efeito: bloqueia o carrinho, cria ou reutiliza a Order, reserva estoque e envia uma única cobrança Point.
- Pré-condições: itens persistidos e não vazios, snapshots válidos, subtotal positivo e coerente com `sum(unitPrice * quantity)`, e tenant coerente entre carrinho, itens, produtos e terminal.
- Idempotência: Order única por carrinho, lock pessimista e `X-Idempotency-Key` igual ao `orderId` local.
- Erros externos: classificação interna em `AUTHENTICATION`, `TERMINAL_NOT_FOUND`, `ACTIVE_CHARGE`, `IDEMPOTENCY_CONFLICT`, `INVALID_REQUEST`, `TIMEOUT`, `UNAVAILABLE` e `UNKNOWN`; resposta pública sanitizada como `502`.
- Resposta `200 PointPaymentResponse`:

```json
{
  "type": "PAYMENT_STATUS",
  "orderId": "uuid-order-local",
  "terminalId": "uuid-terminal-interno",
  "status": "WAITING_PAYMENT",
  "mercadoPagoStatus": "created",
  "transactionId": null,
  "statusDetail": null,
  "message": "Cobrança enviada para a maquininha; pressione o botão verde e siga as instruções"
}
```

## GET /order/{orderId}/status?terminalId={terminalId}

O backend valida a correlação entre Order e Terminal. Para `PENDING`, `CREATED`, `AT_TERMINAL` ou `ACTION_REQUIRED`, consulta a Order Point por `Order.mpOrderId` antes de responder. Falha temporária do Mercado Pago preserva o estado intermediário e retorna `reconciled=false`; status definitivo local não gera consulta desnecessária.

Resposta `PaymentStatusResponse`: `type`, `orderId`, `paymentId`, `terminalId`, `status`, `mercadoPagoStatus`, `transactionId`, `statusDetail`, `message`, `updatedAt` e `reconciled`. Nenhuma credencial OAuth é exposta. Veja [[payment-reconciliation]].

- Finalidade: recuperar o estado após perda/reconexão do WebSocket sem criar nova cobrança.
- Segurança HTTP: pública temporariamente; o terminal informado precisa ser exatamente o terminal do carrinho da Order.
- Resposta: o mesmo `PointPaymentResponse` usado no início e no WebSocket.
- Erro: `404` quando a Order não existe ou pertence a outro terminal.

# Mercado Pago e webhook

## GET /mercado-pago/oauth

- Segurança HTTP: pública temporariamente; requer contexto de empresa para concluir a operação.
- Principal: `@AuthenticationPrincipal User`.
- Resposta: redirect HTTP para autorização Mercado Pago.
- Efeito: grava state Redis `oauth:mp:{uuid}` por dez minutos com gestor e empresa.

## GET /mp/oauth/mercadopago/{idUser}

- Alias depreciado, protegido como gestor.
- `idUser` é ignorado; usa o principal autenticado.

## GET /mercado-pago/oauth/callback e GET /mp/oauth/callback

- Segurança: pública.
- Query: `code`, `state` obrigatórios.
- Resposta: `200` vazio.
- Efeitos: valida state, troca code, cria/atualiza `MercadoPagoConta` e remove state.

## GET /mercado-pago/terminais

- Segurança HTTP: pública temporariamente; requer contexto de empresa para concluir a operação.
- Resposta: lista de `MercadoPagoTerminalResponse` com campos externos e vínculo interno.
- Efeito externo: `GET /terminals/v1/list` usando token da empresa.

## GET /mercado-pago/status

- Finalidade: informar à Home do gestor se a configuração mínima de recebimento Point foi concluída.
- Tenant: não recebe `empresaId`; usa exclusivamente o `EmpresaContext` preenchido pelo JWT.
- Resposta: `MercadoPagoSetupStatusResponse` com `contaVinculada`, `maquininhaVinculada`, `configuracaoCompleta`, `quantidadeTerminais` e `quantidadeMaquininhasVinculadas`.
- Conta vinculada: existe `MercadoPagoConta` da empresa com access token preenchido e autorização ainda não expirada.
- Maquininha vinculada: existe ao menos um `Terminal` da cadeia `Empresa -> Condomínio -> Terminal` com `mercadoPagoTerminalId` não vazio.
- Segurança de dados: não retorna access token, refresh token, client secret ou qualquer credencial OAuth.
- Efeito externo: nenhum; consulta somente o banco local.

## GET /mp/oauth/terminal/{idUser}

- Alias depreciado e protegido; `idUser` é ignorado.

## POST /webhook/mercadopago

- Segurança HTTP: pública, com autenticação própria HMAC.
- Headers: `x-signature`, `x-request-id`; query opcional `data.id`; body JSON do Mercado Pago.
- Respostas: `200 ENFILEIRADO`, `200 DUPLICADO`, `400` payload inconsistente, `401` assinatura inválida, `503` segredo ausente.
- Efeitos: chave Redis de deduplicação por 24 horas e push em `mp_queue`.
- Processamento posterior: `PaymentWorker` → `PagamentoService.atualizarPagamento` → Order/Pagamento/estoque.

# Arquivos e documentação

## POST /files/upload

- Segurança real: pública.
- Entrada: multipart parameter `file`, extensão `.jpg`/`.jpeg` e conteúdo decodificável como imagem; limite global 30 MB.
- Resposta: URL textual relativa `/files/{nome}`.
- Efeito: grava arquivo no filesystem local.

## GET /files/{nome}

- Não é controller: resource handler de `WebConfig`.
- Segurança real: pública.
- Resposta: conteúdo de `uploads/` quando existente.

## GET /

- Controller: `DocController`.
- Segurança real: pública pela regra residual.
- Resposta: redirect para `/swagger-ui/index.html`.

# Testes e debug — habilitados no ambiente de desenvolvimento

Quando `app.test-endpoints.enabled=true` (padrão atual fora do profile `prod`), todos os endpoints abaixo são públicos pela regra residual e fazem chamadas reais à API usando `MP_TEST_ACCESS_TOKEN`. São implementados por `TestarWebhookMPController`/`TestarWebhookMP`. O profile `prod` define a propriedade como `false`, portanto o controller não é registrado nesse profile.

| Endpoint | Entrada | Efeito/saída `200` |
|---|---|---|
| `POST /api/testes/mercadopago/aprovado` | nenhuma | cria order sandbox e simula `processed` |
| `POST /api/testes/mercadopago/sem-fundos` | nenhuma | cria e simula `failed/insufficient_amount` |
| `POST /api/testes/mercadopago/cancelado` | nenhuma | cria e simula `canceled` |
| `POST /api/testes/mercadopago/expirado` | nenhuma | cria e simula `expired` |
| `POST /api/testes/mercadopago/reembolso` | nenhuma | cria, processa e simula `refunded` |
| `POST /api/testes/mercadopago/acao-requerida` | nenhuma | cria e simula `action_required` |
| `POST /api/testes/mercadopago/limpar` | nenhuma | lista e cancela orders presas do terminal configurado |
| `POST /api/testes/mercadopago/teste-real` | `{ carrinhoId, userId, cenario }` | cria Order local de teste e simula cenário |
| `GET /api/testes/mercadopago/listar` | `beginDate`, `endDate`, `status?` | devolve resposta externa como `Map` |
| `POST /api/testes/mercadopago/cancelar/{orderId}` | path orderId | cancela order externa informada |
| `POST /api/testes/mercadopago/cenario?tipo=...` | tipo | delega para cenário conhecido; tipo inválido gera erro |

Falhas HTTP, de conexão ou timeout na integração são traduzidas para resposta sanitizada `502 Bad Gateway`; erros de entrada seguem o tratamento global aplicável. Esses endpoints podem alterar o estado da conta de teste e não devem ser habilitados em produção.

# WebSocket

## /terminal-socket

- WebSocket nativo, origem `*`, sem autenticação.
- Entrada: `{ "terminalId": "uuid", "status": "ONLINE|OFFLINE|MANUTENCAO" }`.
- Efeito: atualiza `status`, `update_at` e `lastPing` por busca global do terminal, com transação e `saveAndFlush`.
- Saída após persistência: `{ "type":"HEARTBEAT_ACK", "terminalId":"uuid", "status":"ONLINE", "lastPing":"ISO_LOCAL_DATE_TIME" }`.

## /payment-socket/{terminalId}

- WebSocket nativo, origem `*`, sem autenticação.
- Saída após commit: `type`, `terminalId`, `orderId`, `transactionId`, `status`, `mercadoPagoStatus`, `statusDetail`, `message` e o campo legado `paid`.
- O fluxo ativo de webhook publica somente quando o estado muda e seleciona a sessão pelo terminal do carrinho.

## /ws e /topic/payment/{terminalId}

- Handshake STOMP sem autenticação, origem por padrão curinga.
- Broker simples em memória; não há `@MessageMapping` de entrada.
- `PaymentSocketService` publica o mesmo `PaymentEvent` após commit em `/topic/payment/{terminalId}`.
