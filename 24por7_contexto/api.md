# API HTTP e canais WebSocket

Voltar para [[00-index]]. Arquitetura em [[arquitetura-geral]], autenticação em [[autenticacao]] e riscos em [[auditoria-bugs]].

> As novas rotas `/admin/**` exigem JWT `ADMIN` ou `GERENTE`. As demais rotas
> ainda seguem temporariamente a regra residual `anyRequest().permitAll()` e
> dependem dos controles de tenant internos quando aplicáveis.

## Convenções observadas

### Promoções e preço

- `GET /promocoes`: filtros opcionais `abrangencia`, `status`, `condominioId`, `produtoId`.
- `POST /promocoes`, `GET/PUT /promocoes/{id}` e `PATCH /promocoes/{id}/status`.
- `GET /promocoes/preco?produtoId=...&condominioId=...` diagnostica o preço atual.
- O corpo usa `nome`, `descricao`, `abrangencia`, `condominioId`, `tipo`, `valor`, `inicio`, `fim`, `ativo`, `prioridade` e `produtoIds`; empresa vem do JWT.
- `POST /carrinho` aceita `expectedUnitPrice`. Aumento divergente retorna `409 PRICE_CHANGED` com o carrinho atualizado.
- Product sync expõe `precoOriginal`, `preco`, `emPromocao`, `promocaoId` e `promocaoNome`.
- Produto expõe `codigoInterno`, `codigosBarras[]` e `fiscal` opcional; `codigo` é alias legado do código principal.
- `POST/PUT /produtos` aceitam JSON quando não há arquivo e multipart quando há foto.
- Respostas e eventos de pagamento expõem `paymentAttemptId`; `orderId` sozinho não identifica retries futuros.

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
| `/empresas/**` | não | sim | `ADMIN` ou `GERENTE`; encerramento valida `ADMIN` no caso de uso |
| `/condominios/**` | não | sim | `ADMIN` ou `GERENTE` |
| `/terminais/**` | não | sim | `ADMIN` ou `GERENTE` |
| `/mercado-pago/**`, `/mp/oauth/**` | não | sim | `ADMIN` ou `GERENTE`; somente callbacks OAuth são públicos e exigem `state` válido |
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
- Segurança HTTP: `ADMIN` ou `GERENTE` autenticado.
- Resposta `200 EmpresaResponse`; `403` se o ID não coincide com `EmpresaContext`.
- Tenant: comparação explícita com empresa autenticada.

## PUT /empresas/{empresaId}

- Controller/service: `EmpresaController.atualizar` → `EmpresaService.atualizar`.
- Segurança HTTP: `ADMIN` ou `GERENTE` autenticado.
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

## Estoque central da Empresa

- `GET /estoque-empresa?busca=&categoria=&ativo=&page=&size=&sort=`: listagem paginada por nome, SKU ou barcode.
- `GET /estoque-empresa/produtos`: catálogo paginado para iniciar um saldo central ainda inexistente.
- `POST /estoque-empresa/{produtoId}/entrada`: entrada positiva com motivo.
- `POST /estoque-empresa/{produtoId}/saida`: saída positiva, registrada no ledger como delta negativo.
- `PUT /estoque-empresa/{produtoId}`: ajuste para um saldo físico absoluto, inclusive negativo.
- `GET /estoque-empresa/movimentacoes`: ledger do estoque central.

## Transferências de estoque

- `POST /transferencias-estoque`: cria rascunho central → condomínio com múltiplos itens.
- `GET /transferencias-estoque?status=&page=&size=&sort=`: histórico paginado.
- `GET /transferencias-estoque/{id}`: detalhe, nome/ID do destino, itens e movimentos vinculados.
- `POST /transferencias-estoque/{id}/confirmar`: confirmação atômica e idempotente.
- `POST /transferencias-estoque/{id}/cancelar`: cancela rascunho sem alterar saldos.

## Planograma central

- `GET/POST /planogramas` e `GET/PUT /planogramas/{id}`.
- `POST /planogramas/{id}/posicoes` e `PUT /planogramas/posicoes/{id}`.
- `POST /planogramas/posicoes/{id}/produtos` posiciona um produto.
- `PUT /planogramas/produtos/{id}/posicao/{posicaoId}` move o posicionamento.
- `DELETE /planogramas/produtos/{id}` faz remoção lógica do posicionamento, sem afetar saldo ou Produto.

## Inventário físico

- `POST /inventarios`: abre uma contagem e cria o snapshot dos itens ativos da localização.
- `GET /inventarios?status=&localTipo=&page=&size=`: histórico paginado.
- `GET /inventarios/{id}`: detalhe, progresso, divergências e conflitos.
- `PUT /inventarios/{id}/itens/{itemId}/contagem`: registra quantidade com três casas e motivo opcional.
- `POST /inventarios/{id}/finalizar`: aplica itens sem conflito de versão, de forma idempotente.
- `POST /inventarios/{id}/cancelar`: cancela sem alterar estoque quando nenhum ajuste parcial existe.

O body de criação usa `localTipo=ESTOQUE_EMPRESA|CONDOMINIO`; `localId` é obrigatório somente para Condomínio. Em contagem cega, o DTO omite saldo/diferença até a revisão permitida. Veja [[inventario]].

## Importação de produtos

- `GET /produtos/importacao/modelo`: XLSX oficial gerado pelo backend.
- `POST /produtos/importacao/validar`: multipart part `file`; valida e devolve preview, sem criar Produto.
- `GET /produtos/importacao/{id}`: progresso e resultado.
- `POST /produtos/importacao/{id}/confirmar`: aceita a execução assíncrona e retorna `202`.
- `GET /produtos/importacao/{id}/erros`: linhas inválidas/processadas com erro.

O modo atual é `SOMENTE_NOVOS`. Arquivo, limites, linhas e tenant são validados no backend; a planilha nunca informa `empresa_id`. Veja [[importacao-produtos]].

Todos esses endpoints derivam a Empresa de `EmpresaContext`; nenhum aceita `empresa_id` como autoridade.
- Observação: campo `ativo` é sempre `true` na projeção agregada.

# Carrinho e checkout temporário

## POST /carrinho

- Controller/service: `CarrinhoController.addCarinho` → `CarrinhoService.save`.
- Segurança real: pública.
- Body `CarrinhoRequest`: `terminalId` e lista `items [{ productId, quantity, receivedWeight, expectedUnitPrice?, codigoBarras? }]`. Quando presente, `codigoBarras` precisa pertencer ao mesmo Produto/Empresa e é congelado no OrderItem; clientes antigos podem omiti-lo.
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
- Concorrência: service faz consulta prévia e a baseline impõe `UNIQUE(carrinho_id)` em `orders`.

## GET /order?carrinho_id={id}

- Segurança real: pública.
- Comportamento real: apesar do nome do query param, o valor é passado a `getOrder` como ID da Order, não do carrinho.
- Resposta: `OrderDetailResponse`; a consulta continua global enquanto não houver `EmpresaContext`.

## GET /pagamento/terminal/{carrinho_id}

- Controller/service: `PagamentoController.getPagamento` → `PagamentoService.gerarCobranca`.
- Segurança real: pública.
- Resposta: `200 true` quando a execução síncrona termina.
- Efeitos: em transação local, reutiliza/cria Order, congela OrderItems, reserva estoque, cria/vincula `PaymentAttempt PENDING` e marca o carrinho `PAYMENT_PENDING`; após o commit chama o Mercado Pago; em nova transação persiste IDs, status e metadados remotos na tentativa.
- Dependências: terminal precisa de `mercadoPagoTerminalId` e empresa de `MercadoPagoConta`.
- Erros: resposta externa inválida gera conflito de estado; HTTP/timeout/indisponibilidade externos são sanitizados como `502`.

Este GET é um alias legado. Novas versões do Terminal devem usar o POST abaixo.

## POST /pagamento/terminal/{carrinho_id}

- Controller/service: `PagamentoController.iniciarPagamentoPoint` → `PagamentoService.gerarCobranca`.
- Segurança HTTP: pública temporariamente; tenant é derivado do carrinho/terminal e comparado com `EmpresaContext` quando presente.
- Efeito: bloqueia o carrinho, cria ou reutiliza a Order, reserva estoque e envia uma única cobrança Point.
- Pré-condições: itens persistidos e não vazios, snapshots válidos, subtotal positivo e coerente com `sum(unitPrice * quantity)`, e tenant coerente entre carrinho, itens, produtos e terminal.
- Idempotência: Order única por carrinho, tentativa persistida antes da chamada externa, lock pessimista por carrinho/Terminal e `X-Idempotency-Key` estável da `PaymentAttempt`.
- Concorrência: no máximo uma Order intermediária por Terminal. Uma nova requisição para outro carrinho recebe `409 PAYMENT_ALREADY_ACTIVE` com `orderId`, `paymentAttemptId`, `cartId` e `paymentStatus` da tentativa existente.
- Erros externos: classificação interna em `AUTHENTICATION`, `TERMINAL_NOT_FOUND`, `ACTIVE_CHARGE`, `IDEMPOTENCY_CONFLICT`, `INVALID_REQUEST`, `TIMEOUT`, `UNAVAILABLE` e `UNKNOWN`; resposta pública sanitizada como `502`.
- Resposta `200 PointPaymentResponse`:

```json
{
  "type": "PAYMENT_STATUS",
  "orderId": "uuid-order-local",
  "paymentAttemptId": "uuid-tentativa-local",
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

Resposta `PaymentStatusResponse`: `type`, `orderId`, `cartId`, `paymentId`, `paymentAttemptId`, `terminalId`, `status`, `mercadoPagoStatus`, `transactionId`, `statusDetail`, `message`, `amount`, `updatedAt` e `reconciled`. `amount` permite recompor o total da tela aprovada após reinício sem duplicar o carrinho no cliente. Nenhuma credencial OAuth é exposta. Veja [[payment-reconciliation]].

- Finalidade: recuperar o estado após perda/reconexão do WebSocket sem criar nova cobrança.
- Segurança HTTP: pública temporariamente; o terminal informado precisa ser exatamente o terminal do carrinho da Order.
- Resposta: o mesmo `PointPaymentResponse` usado no início e no WebSocket.
- Erro: `404` quando a Order não existe ou pertence a outro terminal.

## GET /pagamento/terminal/{terminalId}/ativo

- Finalidade: recuperar pagamento após reinício do processo/Raspberry ou perda dos IDs em memória.
- Segurança HTTP: pública temporariamente; o `terminalId` limita a busca.
- Comportamento: localiza a Order mais antiga em `PENDING`, `CREATED`, `AT_TERMINAL` ou `ACTION_REQUIRED` e chama `PaymentReconciliationService` antes de responder. Tentativa `PENDING` sem ID remoto pode repetir, após cooldown, a mesma submissão e a mesma chave de idempotência.
- Resposta: `200 PaymentStatusResponse`; `204` quando não há tentativa não resolvida.
- Garantia: o endpoint nunca cria uma `PaymentAttempt` nova.

## POST /comprovante

- Finalidade: solicitação explícita, após a compra, de geração e envio do comprovante.
- Segurança HTTP: pública temporariamente, como os endpoints do Terminal; `pedidoId` precisa pertencer exatamente ao `terminalId` informado.
- Pré-condições: `Order.status=PROCESSED`, tentativa mais recente `PaymentAttempt.status=PROCESSED`, `paidAt` existente e snapshot histórico completo.
- Request:

```json
{
  "terminalId": "uuid-terminal",
  "pedidoId": "uuid-order",
  "tipoEnvio": "WHATSAPP",
  "destinatario": "5575999999999"
}
```

`tipoEnvio` aceita `WHATSAPP` ou `EMAIL`. Telefones brasileiros são normalizados para DDI `55`; e-mails são validados e normalizados. O destinatário completo não aparece nos logs.

- Resposta: `status`, `pedido`, `tipoEnvio`, `n8nStatus` e `duplicado`.
- Idempotência: Redis bloqueia envio concorrente para a mesma combinação pedido/canal/destinatário. Um envio confirmado responde `JA_ENVIADO` nas repetições durante o TTL, sem chamar novamente a FastAPI. A chave determinística é enviada como `request_id`, `X-Correlation-Id` e `X-Idempotency-Key` à FastAPI, que a propaga ao n8n. O workflow n8n ainda precisa deduplicar explicitamente pela chave para haver idempotência ponta a ponta.
- Erros estruturados: `COMPROVANTE_INVALID_DESTINATION`, `COMPROVANTE_INVALID_ORDER_DATA`, `COMPROVANTE_ALREADY_PROCESSING`, `COMPROVANTE_IDEMPOTENCY_UNAVAILABLE`, `COMPROVANTE_SERVICE_TIMEOUT` e `COMPROVANTE_SEND_FAILED`.
- Garantia financeira: nenhuma falha desse endpoint altera `Order`, `PaymentAttempt`, estoque ou a aprovação já confirmada.

O contrato entre Spring e Python e a operação Docker estão detalhados em [[comprovante-compra]].

# Mercado Pago e webhook

## GET /mercado-pago/oauth

- Segurança HTTP: `ADMIN` ou `GERENTE` autenticado.
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
- Efeitos: valida state, troca code, aplica vínculo/reautorização/substituição e remove state. Falha de domínio registra apenas o código seguro e temporário para consulta tenant-scoped.

## GET /mercado-pago/oauth/result

- Segurança HTTP: `ADMIN` ou `GERENTE` autenticado.
- Resposta: `200 {code,message}` para a última falha temporária da Empresa ou `204` sem resultado.
- Retenção: dez minutos; um novo início OAuth remove o resultado anterior.

## GET /mercado-pago/terminais

- Segurança HTTP: `ADMIN` ou `GERENTE` autenticado.
- Resposta: lista de `MercadoPagoTerminalResponse` com campos externos e vínculo interno.
- Efeito externo: `GET /terminals/v1/list` usando token da empresa.

## GET /mercado-pago/status

- Finalidade: informar à Home do gestor se a configuração mínima de recebimento Point foi concluída.
- Tenant: não recebe `empresaId`; usa exclusivamente o `EmpresaContext` preenchido pelo JWT.
- Resposta: `MercadoPagoSetupStatusResponse` com os booleanos/contagens anteriores e `contaStatus` (`CONTA_NAO_VINCULADA`, `CONTA_VINCULADA_SEM_POINT`, `POINT_CONFIGURADA`, `CONTA_REVOGADA` ou `CONTA_COM_ERRO`).
- Conta vinculada: existe lease ativo da Empresa, vínculo `ACTIVE`, access token preenchido e autorização ainda não expirada.
- Maquininha vinculada: existe ao menos um `TerminalPointBinding ACTIVE` projetado no Terminal da Empresa.
- Segurança de dados: não retorna access token, refresh token, client secret ou qualquer credencial OAuth.
- Efeito externo: nenhum; consulta somente o banco local.

## DELETE /mercado-pago/conta

- Segurança: `ADMIN` ou `GERENTE`; Empresa vem do JWT/contexto.
- Resposta: `204`.
- Efeito: vínculo histórico `UNLINKED`, tokens limpos, lease ativo removido e Points encerradas. Terminal e catálogo permanecem.
- Conflito: `MERCADO_PAGO_ACTIVE_PAYMENTS` se há pagamento não resolvido.

## DELETE /empresas/me

- Segurança: somente principal `ADMIN` da própria Empresa.
- Resposta: `204` e operação idempotente.
- Efeito: soft close da Empresa, unlink MP/Points, Terminais `RESET_REQUIRED`, evento WebSocket e usuários desativados; histórico financeiro permanece.

## GET /terminal/{terminalId}/bootstrap

- Segurança HTTP: pública como os contratos atuais do equipamento.
- Resposta: `ACTIVE`, `PAYMENT_NOT_CONFIGURED`, `DISABLED`, `RESET_REQUIRED` ou `UNACTIVATED`, com `paymentConfigured`, motivo e timestamp quando aplicável.
- Regra: confirmação autoritativa usada pelo Terminal; ID inexistente retorna `RESET_REQUIRED` no corpo 200.

## POST /terminal/{terminalId}/factory-reset/started

- Segurança HTTP: pública como os contratos atuais do equipamento.
- Resposta: `204`; confirmação idempotente do início.

## POST /terminal/{terminalId}/factory-reset/completed

- Segurança HTTP: pública como os contratos atuais do equipamento.
- Resposta: `204`; muda `RESET_REQUIRED` para `UNACTIVATED` e libera a identidade de ativação antiga.

## GET /mp/oauth/terminal/{idUser}

- Alias depreciado e protegido; `idUser` é ignorado.

## POST /webhook/mercadopago

- Segurança HTTP: pública, com autenticação própria HMAC.
- Headers: `x-signature`, `x-request-id`; query opcional `data.id`; body JSON do Mercado Pago.
- Respostas: `200 ENFILEIRADO`, `200 DUPLICADO`, `400` payload inconsistente, `401` assinatura inválida, `503` segredo ausente.
- Efeitos: registra/deduplica `webhook_event` no MySQL, mantém chave Redis curta, faz push em `mp_queue` e atualiza o inbox para `QUEUED/PROCESSED/DLQ`.
- Processamento posterior: `PaymentWorker` → `PagamentoService.atualizarPagamento` → `PaymentStateTransitionService` → PaymentAttempt/PaymentEvent/Order/estoque.

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

# Telemetria de Terminal

## GET /terminal/health

Endpoint leve usado pelo Terminal para medir alcance e latência da API. Retorna `status=UP` e timestamp. Não representa a saúde da Point.

## POST /terminal/telemetry

Recebe o UUID provisionado, `capturedAt` e os blocos `system`, `network`, `application` e `display`. Valida Terminal existente/ativo, rejeita timestamp mais de cinco minutos no futuro, grava histórico, atualiza o estado atual somente quando a amostra é mais nova e reconcilia alertas. Empresa e condomínio não são aceitos como autoridade no body.

## GET /terminais/monitoramento

Endpoint administrativo e multi-tenant. Aceita `condominioId` e `status=SAUDAVEL|ATENCAO|CRITICO|OFFLINE`. O resumo considera todos os Terminais dentro do filtro de condomínio; o filtro de status restringe a lista.

## GET /terminais/{id}/telemetria

Retorna presença, classificação, motivos, métricas atuais e alertas ativos, sempre validando `Terminal -> Condomínio -> EmpresaContext`.

## GET /terminais/{id}/telemetria/historico?period=1h|6h|24h|7d

Retorna somente os pontos necessários aos gráficos: temperatura, CPU, RAM, disco, latência e sinal Wi-Fi, em ordem cronológica.

## GET /terminais/{id}/telemetria/alertas

Retorna as 100 ocorrências mais recentes, ativas e resolvidas, sem apagar o histórico operacional.

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

# Administração agregada

As rotas desta seção exigem JWT de usuário `ADMIN` ou `GERENTE`, derivam a
Empresa exclusivamente de `EmpresaContext` e retornam DTOs, nunca entities.

## GET /admin/dashboard

Retorna `AdminDashboardResponse`: nome da Empresa/usuário, instante de geração,
vendas e faturamento do dia, Terminais, alertas, estoque, pagamentos em atenção,
checklist e até oito atividades recentes. É o único snapshot necessário para
a Home Flutter.

## GET /admin/onboarding-status

Retorna `AdminOnboardingStatusResponse`. Todos os valores são derivados de
Empresa, Condomínio, Terminal, setup Mercado Pago, Produto e
`EstoqueCondominio`; não há estado de checklist persistido.

## GET /admin/terminals/summary

Retorna `AdminTerminalSummaryResponse` com `total`, `online`, `offline` e
`comAlerta`. Considera Terminais ativos e usa o threshold configurado de
telemetria para classificar `lastPing`.

## GET /admin/alerts/summary e GET /admin/alerts

O resumo retorna `ativos`, `info`, `warning` e `critical`. A listagem aceita
`status`, `page`, `size` e `sort`; limita a página a 100 registros e permite
ordenar por `openedAt`, `lastObservedAt`, `status` ou `type`.

## GET /admin/payments/attention-summary e GET /admin/payments

O resumo considera somente a tentativa mais nova de cada Order e conta
`ACTION_REQUIRED`, `PENDING` vencido pelo threshold e
`FAILED/processing_error`. A listagem aceita `from`, `to`, `status`, `provider`,
`terminalId`, `orderId`, paginação e ordenação validada.

## GET /admin/sales/summary e GET /admin/sales

O resumo aceita `period=TODAY|7D|30D` ou `from`/`to` e retorna quantidade,
faturamento e ticket médio. Somente `Order.PROCESSED` por `paidAt` entra no
faturamento. A listagem acrescenta filtros `condominioId`, `terminalId` e
`status`; quando o status é `PROCESSED`, o período também se aplica a `paidAt`.

## GET /admin/activity?size=20

Retorna até 30 `AdminActivityResponse` derivados de ações conhecidas do
`AuditLog`. Dados técnicos `before_data`, `after_data` e metadata não são
expostos.

Detalhes das regras e limitações: [[admin-dashboard]].

# Branding da Empresa

## GET /empresas/me/branding

Exige JWT `ADMIN` ou `GERENTE`. Retorna nome de exibição, URLs de logo e cores
da Empresa derivada de `EmpresaContext`. Não recebe `empresaId`.

## PUT /empresas/me/branding

Exige o mesmo papel e recebe `nomeExibicao`, `logoUrl`, `logoDarkUrl`,
`corPrincipal`, `corSecundaria` e `corDestaque`. Logos, quando preenchidos,
devem usar HTTPS; cores usam `#RRGGBB`. Nenhuma credencial OAuth é aceita ou
retornada.
# Leads comerciais públicos

`POST /public/leads` recebe solicitações do site comercial. A rota é pública, valida os campos obrigatórios, aplica honeypot e rate limiting e persiste cada contato com status inicial `NEW`. Veja [[marketing-leads]].
