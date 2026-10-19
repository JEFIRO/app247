# API HTTP e canais WebSocket

## Autenticação operacional do Terminal — 27/09/2026

Além das rotas financeiras já documentadas, exigem a credencial individual em
`X-Terminal-Token`: `GET /produtos/sync`, `GET /produtos/home`,
`GET /terminais/{id}/produtos-disponiveis`, lifecycle, telemetria, comprovante e os
upgrades `/payment-socket/{id}`, `/terminal-socket` e `/ws`. A credencial resolve o
Terminal; UUID/body/path nunca escolhem a identidade. `/terminal/serial/{serial}`
continua público somente para descoberta.

Voltar para [[00-index]]. Arquitetura em [[arquitetura-geral]], autenticação em [[autenticacao]] e riscos em [[auditoria-bugs]].

> As rotas `/admin/**` exigem JWT `ADMIN` ou `GERENTE`, `/app-cliente/**` e
> `/user/**` exigem JWT, e as rotas operacionais do Terminal exigem device
> credential. A regra residual é `denyAll`; veja [[security/public-endpoints]].

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
| `/admin/app-cliente/**` | não | sim | `ADMIN` ou `GERENTE`; Empresa sempre deriva do JWT |
| CRUD gestor de `/produtos/**`, estoques e operações | não | sim | `ADMIN` ou `GERENTE` |
| sync/home/disponibilidade do Terminal | não | não | device credential do Terminal |
| `/user/recuperar`, `/user/validar`, `/user/redefinir-senha` | sim | não | rate limit + TTL + uso único |
| demais `/user/**` | não | sim | self ou gestor da mesma Empresa |
| `POST /carrinho` e rotas de pagamento/status/cancelamento do Terminal | não | não | `X-Terminal-Token` + identidade do Terminal |
| demais `/carrinho/**`, checkout operacional e `/order/**` | não | não | device credential + ownership exato |
| checkout móvel `/checkout` e `/checkout/session` | não | não | bloqueado/legado |
| `GET /files/**` | sim | não | leitura pública; upload exige gestor |
| `/api/testes/mercadopago/**` | não | sim | `ADMIN` ou `GERENTE` |
| `/app-cliente/**` | não | sim | `USER` (inclui `ADMIN` e `GERENTE`) |
| `/terminal/customer-link/consume`, `/terminal/coupons/apply` | não | não | device credential individual em `X-Terminal-Token` |
| `/webhook/**`, ativação por serial e Swagger | sim | não | nenhum |

O inventário completo das exceções públicas está em [[security/public-endpoints]] e
as decisões de ownership em [[security/http-authorization]].

# App Cliente

Os contratos móveis obrigatórios estão implementados sob `/app-cliente` e não
aceitam IDs de contexto no request. Incluem bootstrap, catálogo paginado,
histórico/detalhe/comprovante/suporte de compras, identificação temporária,
fidelidade, recompensas/resgates, cupons/tokens e preferências. A lista completa,
regras de ownership, atomicidade e consumidores do Terminal estão em
[[app-cliente]].

## Gestão do App Cliente

Todas as rotas abaixo exigem JWT `ADMIN` ou `GERENTE`. Nenhuma aceita `empresaId`:

| Método | Rota | Contrato |
|---|---|---|
| GET | `/admin/app-cliente/config` | Lê as flags explícitas da Empresa; ausência retorna defaults seguros desativados. |
| PUT | `/admin/app-cliente/config` | Atualiza catálogo, promoções, fidelidade, QR, preferências e suporte. |
| GET | `/admin/app-cliente/fidelidade` | Lê estado, modo e taxa da Empresa. |
| PUT | `/admin/app-cliente/fidelidade` | Valida e grava `NONE`, `POINTS` ou `CASHBACK`. |
| GET | `/admin/app-cliente/recompensas` | Lista paginada; aceita `page`, `size` e `ativo`. |
| POST | `/admin/app-cliente/recompensas` | Cria recompensa da Empresa autenticada. |
| GET | `/admin/app-cliente/recompensas/{id}` | Consulta por ID e Empresa; cross-tenant retorna 404. |
| PUT | `/admin/app-cliente/recompensas/{id}` | Atualiza campos e validade no tenant. |
| DELETE | `/admin/app-cliente/recompensas/{id}` | Soft delete (`ativo=false`). |

O request de configuração não contém dados financeiros; o endpoint de fidelidade é a
fonte de verdade para modo e taxas. Recompensas usam `PERCENTUAL` ou `VALOR_FIXO`,
`BigDecimal`, mínimo de compra obrigatório e desconto máximo opcional. Erros de
negócio incluem `INVALID_LOYALTY_MODE`, `INVALID_LOYALTY_CONFIG`, `INVALID_REWARD` e
`REWARD_NOT_FOUND`. Contratos detalhados: [[app-cliente/app-client-config]],
[[app-cliente/loyalty-config]] e [[app-cliente/rewards]].

# Autenticação

## POST /auth/login

- Controller/service: `AuthController.login` → `UserService.login`.
- Finalidade: autenticar CPF e senha e emitir JWT.
- Segurança: pública.
- Body `AuthDTO`: `cpf` e `senha`, ambos obrigatórios.
- Resposta `200`: `AuthResponse { token, user: UserResponseDTO }`.
- Erros: `400` para validação; `401` para credenciais inválidas; `429 LOGIN_RATE_LIMITED` quando o CPF
  acumulou falhas (padrão 5 em 15 min, conta mesmo com a senha certa até a janela expirar; login bem-sucedido
  zera) ou a origem passou de 50 tentativas em 15 min. Vale também para `/auth/login/admin`.
- Efeitos: autenticação via `AuthenticationManager`; não altera `ultimoLogin`.
- Tenant: `empresaId` é incluído no token a partir do usuário.

## POST /auth/login/admin

- Controller/service: `AuthController.loginAdmin` → `UserService.loginAdmin`.
- Finalidade: login exclusivo de gestor.
- Segurança: pública; a role é verificada depois da senha.
- Body/saída: os mesmos de `/auth/login`.
- Resposta: `200`; `401` para credenciais inválidas; `403` se não for `ADMIN`/`GERENTE`; `429` como em `/auth/login`.
- Efeito: emite o mesmo tipo de JWT.

## POST /auth/register

- Controller/service: `AuthController.create` → `RegistrationService.register`.
- Finalidade: cadastrar uma conta global `USER`, ainda sem Empresa/Condomínio.
- Segurança HTTP: pública, com rate limit por IP/e-mail/CPF.
- Body `ClientRegistrationRequest`: nome/sobrenome, e-mail, senha, CPF, telefone e aceites legais quando configurados; não aceita role nem IDs de tenant.
- Resposta: `201 ClientRegistrationResponse` com `user`, `cpfVerificationStatus = UNVERIFIED`, `condominio = null`, `requiresLogin = true` e o estado dos documentos legais. O cadastro não emite JWT; o cliente segue para `/auth/login`.
- Erros estruturados: `400 INVALID_REGISTRATION_DATA`/`INVALID_CPF`, `409 CPF_ALREADY_REGISTERED`/`EMAIL_ALREADY_REGISTERED` e `429 REGISTRATION_RATE_LIMITED`.
- Efeitos: senha BCrypt, insert em `users` com `cpf_verified_at = NULL` e `UserCreatedEvent`.
- Tenant: permanece nulo até o consumo de convite autorizado.

## POST /admin/app-cliente/tenant-invitations

- Segurança: JWT `ADMIN` ou `GERENTE`.
- Body: `cpf`, `condominioId`.
- Tenant: Empresa sempre deriva do JWT; o Condomínio precisa pertencer a ela e estar ativo.
- Resposta: `201`, token opaco de uso único, expiração e ID do Condomínio.
- Persistência: somente hashes do token e CPF são armazenados.

## POST /app-cliente/tenant-link

- Segurança: JWT `USER`, inclusive o JWT global emitido antes do vínculo.
- Body: somente `token`; não aceita Empresa ou Condomínio.
- Efeito: valida CPF/tenant, vincula a conta, marca o CPF verificado, executa claim e emite JWT novo com `empresaId`.
- Resposta: `200`, status, novo access token, tenant resolvido, estado do CPF e resultado do claim.
- Erros opacos: convite inválido, expirado, usado por outra conta ou CPF divergente retorna `404 TENANT_INVITATION_INVALID`.

## POST /auth/send-code

- Controller/service: `AuthController.sendCode` → `UserService.sendCode`.
- Finalidade: enviar código de validação de e-mail.
- Segurança: pública.
- Body `ValidateEmailRequest` (grupo `SendCode`): `email` obrigatório, formato de e-mail, até 180;
  `nome` obrigatório, até 100; `code` é ignorado. Inválido → `400 VALIDATION_ERROR`.
- Resposta: `200` vazio.
- O `nome` entra no HTML do e-mail com escape (`EmailService.html`).
- `429 EMAIL_CODE_RATE_LIMITED`: padrão 3 envios por e-mail e 20 por origem a cada 15 min.
- Efeitos: grava `email_validation_queue:{email}` com TTL de 15 minutos e enfileira na lista Redis homônima.
- Observação: não marca `User.emailVerificado`.

## POST /auth/validate-code

- Controller/service: `AuthController.validateCode` → `UserService.verificarCode(ValidateEmailRequest)`.
- Finalidade: comparar código de e-mail.
- Segurança: pública.
- Body (grupo `VerifyCode`): `email` obrigatório; `code` obrigatório com 6 dígitos; `nome` opcional.
  Inválido → `400 VALIDATION_ERROR`.
- Resposta: `200` vazio.
- Erros: código expirado/inválido (`ExpiredCodeException`/`InvalidCodeException`); `429 EMAIL_CODE_RATE_LIMITED`
  após 5 tentativas sem acerto por e-mail em 15 min; nesse momento o código atual é apagado e é preciso
  pedir outro. Acerto zera o contador.
- Efeito: remove a chave Redis quando válido.

# Recuperação e usuários

## POST /user/recuperar

- Controller/service: `UserController.recuperar` → `PasswordRecoveryService.request`.
- Segurança: pública.
- Body `PasswordRecovery`: `cpf` com 11 dígitos.
- Resposta: `202 { "message": "Se o CPF existir, enviamos instruções para recuperação." }`.
- Efeitos: normaliza CPF, aplica limite por hash de CPF+origem e, quando existe conta,
  armazena somente HMAC do código aleatório com TTL.
- Anti-enumeração: conta existente e inexistente têm status e corpo equivalentes.

## POST /user/validar

- Controller/service: `UserController.validar` → `PasswordRecoveryService.validateCode`.
- Segurança: pública.
- Body `ValidateCodeRequest`: `code`, `cpf`; email/nome são opcionais para esta operação.
- Resposta: `200 { "token": "valor-opaco" }`.
- Efeitos: consome o código atomicamente e armazena somente HMAC de token aleatório
  de 32 bytes; novo token invalida o anterior.
- Erros: `400 PASSWORD_RESET_INVALID`; `429 PASSWORD_RESET_RATE_LIMITED`.

## POST /user/redefinir-senha

- Controller/service: `UserController.redefinirSenha` → `PasswordRecoveryService.resetPassword`.
- Segurança: pública.
- Body `ResetPasswordRequest`: CPF válido, token obrigatório e `novaSenha` de 8–100 caracteres.
- Resposta: `200` com mensagem.
- Efeitos: token é consumido atomicamente uma única vez e a senha é gravada com BCrypt.
- Erros: `400 PASSWORD_RESET_INVALID`; `429 PASSWORD_RESET_RATE_LIMITED`.

## POST /user/alterar-senha

- Controller/service: `UserController.alterarSenha` → `UserService.alterarSenha`.
- Segurança: JWT; `userId` deve ser o principal autenticado.
- Body `ChangePasswordRequest`: `userId` e `oldPassword` obrigatórios; `newPassword` obrigatório, 8 a 72
  caracteres e diferente da atual (`400 PASSWORD_UNCHANGED`). Desde 18/10/2026.
- Resposta: `200 { "message": "Senha alterada com sucesso" }`.
- Efeitos: atualiza senha do ID informado se a senha antiga corresponder.
- Tenant/ownership: estritamente self-service.

## GET /user/{userId}/orders

- Controller/service: `UserController.getOrdersByUser` → `UserService.getOrderByUser`.
- Segurança: JWT; self ou `ADMIN`/`GERENTE` da mesma Empresa.
- Path: `userId`; query: `page`, `size`, `sort`.
- Resposta: `Page<OrderDTO>` com ID, status, valores e data.
- Tenant: query inclui usuário e Empresa; alvo não autorizado retorna 404.

## GET /user/{userId}

- Controller/service: `UserController.getUser` → `UserService.getUser`.
- Segurança: JWT; self ou `ADMIN`/`GERENTE` da mesma Empresa.
- Resposta: `UserResponseDTO`, sem senha e sem relações JPA.
- Erro: `404` se inexistente.
- Tenant: lookup e autorização incluem a Empresa.

## POST /user/foto?id={userId}

- Controller/service: `UserController.salvar` → `UserService.salvarFoto`.
- Segurança: JWT; somente o próprio usuário.
- Entrada multipart: part `file`; query `id`.
- Resposta: `200 true`.
- Efeitos: grava JPG/JPEG em `uploads/` e altera `fotoPerfil` do ID informado.
- Tenant: identidade vem do principal.

## PUT /user/{id}

- Controller/service: `UserController.update` → `UserService.atualizarUsuario`.
- Segurança: JWT; self ou gestor da mesma Empresa.
- Body `UserUpdate`: nome, sobrenome, email, telefone, ativo e condominioId, todos opcionais; campo
  presente é validado (nome não vazio, até 100; e-mail válido, até 180; telefone 10–20 caracteres
  de dígitos/`()+-`). Inválido → `400 VALIDATION_ERROR`.
- Resposta: `200` com mensagem.
- Regras (desde 18/10/2026, para impedir tomada de conta via recuperação de senha):
  - e-mail só pode ser alterado pelo próprio usuário (`403 EMAIL_CHANGE_SELF_ONLY`); reenviar o mesmo
    e-mail (ignorando maiúsculas) não conta como alteração. E-mail é normalizado (trim/minúsculas),
    duplicado → `409 EMAIL_ALREADY_REGISTERED`, e a troca zera `emailVerificado`;
  - GERENTE não altera ADMIN (`403 USER_MANAGEMENT_FORBIDDEN`);
  - `ativo` só vale para terceiros e só por gestor; na própria conta é ignorado;
  - `condominioId` somente por gestor.
- Tenant: o condomínio e o usuário alvo devem pertencer à Empresa autenticada.

# Onboarding, empresas e condomínios

## POST /onboarding e POST /condominio

- Controller/service: `OnboardingController.criar` → `OnboardingService.executar`.
- Segurança: pública; `/condominio` é alias legado.
- Body `CadastroCompletoRequest`: `gestor` (alias `user`), `empresa`, `condominio`, `terminal`.
- Resposta `201 OnboardingResponse`: IDs do gestor, empresa, condomínio e terminal.
- Efeitos: cria toda a hierarquia em uma transação; gestor recebe `ADMIN` e senha BCrypt.
- Erros: validação `400`, conflitos de CPF/CNPJ/email/serial conforme constraints; parte dos conflitos vira `500`;
  `429 ONBOARDING_RATE_LIMITED` após 5 cadastros válidos da mesma origem por hora (corpo inválido não conta).

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
- Segurança HTTP: JWT `ADMIN`/`GERENTE` (`SecurityConfig`); operação restrita à Empresa do token.
- Body `CondominioRequest`: `nome` obrigatório (até 150); `cnpj` vazio, 14 dígitos ou `00.000.000/0000-00`;
  `endereco` opcional, mas se vier exige rua, número, bairro, cidade, UF (2 letras) e CEP (`00000-000` ou 8 dígitos).
  Inválido → `400 VALIDATION_ERROR` (antes caía no banco e voltava `409 DATA_CONFLICT`).
- Resposta: `201 CondominioResponse`.
- Efeito: cria condomínio/endereço na empresa autenticada.

## GET /condominios

- Finalidade: listar condomínios da empresa autenticada.
- Controller/service: `CondominioController.listar` → `CondominioService.listar`.
- Segurança HTTP: JWT `ADMIN`/`GERENTE` (`SecurityConfig`); operação restrita à Empresa do token.
- Resposta: `200 List<CondominioResponse>`.

## GET /condominios/{condominioId}

- Finalidade: buscar condomínio dentro do tenant.
- Segurança HTTP: JWT `ADMIN`/`GERENTE` (`SecurityConfig`); operação restrita à Empresa do token.
- Resposta: `200 CondominioResponse`; `404` se ausente ou de outra empresa.

## PUT /condominios/{condominioId}

- Finalidade: atualizar condomínio/endereço no tenant.
- Segurança HTTP: JWT `ADMIN`/`GERENTE` (`SecurityConfig`); operação restrita à Empresa do token.
- Body/saída: `CondominioRequest` → `CondominioResponse`.
- Efeitos: substitui o endereço por nova entidade quando enviado.

# Terminais

## GET /terminal/serial/{serial}

- Controller/service: `TerminalController.bySerial` → `TerminalService.getBySerial`.
- Segurança: pública.
- Resposta `TerminalActivationResponse` (reduzida em 18/10/2026): somente `terminalId`, `serialNumber`,
  `ativo` e `activated`. Nome, código, status, lifecycle e Condomínio deixaram de ser públicos;
  o Terminal Python só lê `terminalId`, `activated` e `ativo`.
- Erro: `404` se serial inexistente.
- Tenant: não requer contexto; destina-se à ativação física.

## POST /condominios/{condominioId}/terminais

- Segurança HTTP: JWT `ADMIN`/`GERENTE` (`SecurityConfig`); operação restrita à Empresa do token.
- Body `TerminalRequest`: `nome` obrigatório até 80 (vira `codigo`), `serialNumber` até 120, `macAddress` até 30,
  `ipAddress` até 45. Mesmas regras no `PUT` e no onboarding.
- Resposta: `201 TerminalResponseDTO`.
- Efeito: cria terminal no condomínio validado contra `EmpresaContext`.

## GET /condominios/{condominioId}/terminais

- Segurança HTTP: JWT `ADMIN`/`GERENTE` (`SecurityConfig`); operação restrita à Empresa do token.
- Resposta: lista de `TerminalResponseDTO` limitada ao condomínio/empresa.

## GET /terminais/{terminalId}

- Segurança HTTP: JWT `ADMIN`/`GERENTE` (`SecurityConfig`); operação restrita à Empresa do token.
- Resposta: `TerminalResponseDTO`; `404` também oculta terminal de outro tenant.

## PUT /terminais/{terminalId}

- Segurança HTTP: JWT `ADMIN`/`GERENTE` (`SecurityConfig`); operação restrita à Empresa do token.
- Body/saída: `TerminalRequest` → `TerminalResponseDTO`.
- Efeito: atualiza dados físicos sem alterar condomínio.

## PUT /terminais/{terminalId}/mercado-pago

- Controller/service: `TerminalController.vincularMercadoPago` → `MercadoPagoTerminalService.vincular`.
- Segurança HTTP: JWT `ADMIN`/`GERENTE` (`SecurityConfig`); operação restrita à Empresa do token.
- Body: `{ "mercadoPagoTerminalId": "..." }`.
- Resposta: `200 TerminalResponseDTO`.
- Efeitos: consulta a conta Mercado Pago da empresa, valida a maquininha e grava vínculo único.
- Erros externos/negócio: conta ausente, maquininha ausente na conta, vínculo duplicado ou HTTP externo; normalmente `500`.

## DELETE /terminais/{terminalId}/mercado-pago

- Segurança HTTP: JWT `ADMIN`/`GERENTE` (`SecurityConfig`); operação restrita à Empresa do token.
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
- Validação: `nome` até 180, `descricao` até 1000, `codigo`/`codigoInterno` até 80, preço até 9 inteiros e 6
  decimais, peso/tolerância até 12 inteiros e 3 decimais, `foto` até 500 e sem esquemas `javascript:`/`data:`/`vbscript:`.
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
- Segurança: device credential do `terminalId` informado; o ID não é autoridade.
- Body `CarrinhoRequest`: `terminalId` e lista `items [{ productId, quantity, receivedWeight, expectedUnitPrice?, codigoBarras? }]`. Quando presente, `codigoBarras` precisa pertencer ao mesmo Produto/Empresa e é congelado no OrderItem; clientes antigos podem omiti-lo.
- Validação: `terminalId` obrigatório; lista não nula/não vazia; item e `productId` obrigatórios; quantidade inteira positiva.
- Resposta: `200 CarrinhoResponseDTO` com itens snapshot.
- Efeitos: valida terminal, força produtos à empresa dele, rejeita quantidade não positiva e produto duplicado, calcula subtotal e persiste itens por cascade.
- Erros: terminal/produto inexistente, lista/itens nulos e duplicidade; em geral `500` por falta de handlers.

## GET /carrinho/{id}

- Segurança: device credential; compara o Terminal exato persistido no carrinho.
- Resposta: `200 CarrinhoResponseDTO`; Terminal alheio recebe 403.

## GET /checkout/carrinho?idCarrinho={id}

- Segurança: device credential e ownership do carrinho.
- Efeito: cria sessão Redis de 15 minutos para o carrinho.
- Resposta `CheckoutSessionResponseDTO` com sessionId, carrinhoId, terminalId, URL `app24por7://session/{id}`, status e expiração.

## GET /checkout/session?idSession={id}

- Segurança: rota legada bloqueada por `SecurityConfig`.

## GET /checkout/qrcode?id={sessionId}

- Segurança: device credential; a sessão deve pertencer ao Terminal autenticado.
- Resposta: `200 image/png` contendo `app247://session/{id}`.
- Divergência: o DTO textual usa esquema `app24por7://`, enquanto o QR usa `app247://`.

## GET /checkout?id={userId}&session={sessionId}

- Segurança: rota legada bloqueada por `SecurityConfig`.
- Motivo: o App Cliente não compra pelo celular e `userId` enviado pelo cliente não
  pode determinar ownership.

# Pedidos e pagamentos

## GET /order/finalizar?carrinho_id={id}

- Controller/service: `OrderController.order` → `OrderService.createOrder`.
- Segurança: device credential + ownership do carrinho persistido.
- Resposta: `OrderDetailResponse` com resumo do pagamento e snapshot do carrinho.
- Efeitos: exige carrinho `OPEN`, muda para `READY_FOR_PAYMENT`, cria uma Order e publica reserva síncrona de estoque.
- Concorrência: service faz consulta prévia e a baseline impõe `UNIQUE(carrinho_id)` em `orders`.

## GET /order?carrinho_id={id}

- Segurança: device credential + ownership da Order persistida.
- Comportamento real: apesar do nome do query param, o valor é passado a `getOrder` como ID da Order, não do carrinho.
- Resposta: `OrderDetailResponse`; a consulta continua global enquanto não houver `EmpresaContext`.

## GET /pagamento/terminal/{carrinho_id}

- Controller/service: `PagamentoController.getPagamento` → `PagamentoService.gerarCobranca`.
- Segurança: `X-Terminal-Token` e `X-Terminal-Id`; alias legado marcado deprecated.
- Resposta: `200 true` quando a execução síncrona termina.
- Efeitos: em transação local, reutiliza/cria Order, congela OrderItems e totais,
  valida/reserva o cupom, reserva estoque, cria/vincula `PaymentAttempt PENDING` e
  marca o carrinho `PAYMENT_PENDING`; após o commit chama o Mercado Pago; em nova
  transação persiste IDs, status e metadados remotos na tentativa.
- Dependências: terminal precisa de `mercadoPagoTerminalId` e empresa de `MercadoPagoConta`.
- Erros: resposta externa inválida gera conflito de estado; HTTP/timeout/indisponibilidade externos são sanitizados como `502`.

Este GET é um alias legado. Novas versões do Terminal devem usar o POST abaixo.

## POST /pagamento/terminal/{carrinho_id}

- Controller/service: `PagamentoController.iniciarPagamentoPoint` → `PagamentoService.gerarCobranca`.
- Segurança: `X-Terminal-Token` e `X-Terminal-Id`; o backend carrega o Terminal e
  valida Carrinho, Order, Condomínio e Empresa. Cross-tenant responde 404.
- Efeito: bloqueia o carrinho, cria ou reutiliza a Order, congela o preço, reserva
  estoque e envia uma única cobrança Point. O request não informa total nem novo cupom.
- Pré-condições: itens persistidos e não vazios, snapshots válidos, subtotal positivo e coerente com `sum(unitPrice * quantity)`, e tenant coerente entre carrinho, itens, produtos e terminal.
- Idempotência: Order única por carrinho, tentativa persistida antes da chamada externa,
  lock pessimista por carrinho/Terminal, tentativa ativa única no MySQL e
  `X-Idempotency-Key` estável da `PaymentAttempt`.
- Concorrência: no máximo uma Order intermediária por Terminal. Uma nova requisição para outro carrinho recebe `409 PAYMENT_ALREADY_ACTIVE` com `orderId`, `paymentAttemptId`, `cartId` e `paymentStatus` da tentativa existente.
- Retry: `FAILED`, `CANCELED` e `EXPIRED` criam novo attempt. `PENDING`,
  `ACTION_REQUIRED`, `UNKNOWN` e `CANCELLING` bloqueiam outro attempt.
- Erros estruturados incluem `PAYMENT_ALREADY_ACTIVE`, `ORDER_PRICE_NOT_FINALIZED`,
  `CART_NOT_OWNED`, `ORDER_NOT_OWNED` e `COUPON_*`. Erros externos são sanitizados.
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
  "message": "Cobrança enviada para a maquininha; pressione o botão verde e siga as instruções",
  "remoteOrderId": "id-remoto-ou-null",
  "cancellationRequested": false,
  "attemptStatus": "PENDING"
}
```

## GET /order/{orderId}/status?terminalId={terminalId}

Exige `X-Terminal-Token`. O backend valida a correlação entre Order e Terminal. Para
estado não terminal, consulta a Order Point quando há ID remoto. `UNKNOWN` sem ID pode
reenviar tecnicamente o mesmo attempt/chave após cooldown. Falha temporária preserva
o bloqueio e retorna `reconciled=false`; status definitivo local não gera consulta.

Resposta `PaymentStatusResponse`: `type`, `orderId`, `cartId`, `paymentId`, `paymentAttemptId`, `terminalId`, `status`, `mercadoPagoStatus`, `transactionId`, `statusDetail`, `message`, `amount`, `updatedAt` e `reconciled`. `amount` permite recompor o total da tela aprovada após reinício sem duplicar o carrinho no cliente. Nenhuma credencial OAuth é exposta. Veja [[payment-reconciliation]].

- Finalidade: recuperar o estado após perda/reconexão do WebSocket sem criar nova cobrança.
- Segurança: `X-Terminal-Token`; o terminal informado precisa ser exatamente o Terminal
  persistido do carrinho da Order.
- Resposta: o mesmo `PointPaymentResponse` usado no início e no WebSocket.
- Erro: `404` quando a Order não existe ou pertence a outro terminal.

## GET /pagamento/terminal/{terminalId}/ativo

- Finalidade: recuperar pagamento após reinício do processo/Raspberry ou perda dos IDs em memória.
- Segurança: `X-Terminal-Token`; o `terminalId` identifica e autoriza o Terminal.
- Comportamento: localiza a Order mais antiga não resolvida e chama
  `PaymentReconciliationService`. Attempt `UNKNOWN` sem ID remoto pode repetir, após
  cooldown, a mesma submissão e chave; nunca cria novo attempt.
- Resposta: `200 PaymentStatusResponse`; `204` quando não há tentativa não resolvida.
- Garantia: o endpoint nunca cria uma `PaymentAttempt` nova.

## POST /comprovante

- Finalidade: solicitação explícita, após a compra, de geração e envio do comprovante.
- Segurança HTTP: device credential; `pedidoId` e `terminalId` devem pertencer
  exatamente ao Terminal autenticado.
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

- Segurança HTTP: device credential; aceita Terminal em estado de lifecycle não
  operacional para informar `DISABLED/RESET_REQUIRED`, mas rejeita credencial revogada.
- Resposta: `ACTIVE`, `PAYMENT_NOT_CONFIGURED`, `DISABLED`, `RESET_REQUIRED` ou `UNACTIVATED`, com `paymentConfigured`, motivo e timestamp quando aplicável.
- Regra: confirmação autoritativa usada pelo Terminal; o path deve corresponder à credencial.

## POST /terminal/{terminalId}/factory-reset/started

- Segurança HTTP: device credential correspondente ao path.
- Resposta: `204`; confirmação idempotente do início.

## POST /terminal/{terminalId}/factory-reset/completed

- Segurança HTTP: device credential correspondente ao path; a conclusão revoga a credencial.
- Resposta: `204`; muda `RESET_REQUIRED` para `UNACTIVATED` e libera a identidade de ativação antiga.

## GET /mp/oauth/terminal/{idUser}

- Alias depreciado e protegido; `idUser` é ignorado.

## POST /webhook/mercadopago

- Segurança HTTP: pública, com autenticação própria HMAC.
- Headers: `x-signature`, `x-request-id`; query opcional `data.id`; body JSON do Mercado Pago.
- Respostas: `200 ENFILEIRADO`, `200 DUPLICADO`, `400` payload inconsistente, `401` assinatura inválida, `503` segredo ausente.
- Efeitos: registra/deduplica `webhook_event` no MySQL, mantém chave Redis curta, faz push em `mp_queue` e atualiza o inbox para `QUEUED/PROCESSED/DLQ`.
- Processamento posterior: `PaymentWorker` → `PagamentoService.atualizarPagamento` → consulta `GET /v1/orders/{data.id}`
  com a conta da Empresa da tentativa (o corpo do webhook não é confiável) → `PaymentStateTransitionService` →
  PaymentAttempt/PaymentEvent/Order/estoque. Ver [[mercado-pago]].

## POST /admin/orders/{orderId}/refund

- Segurança: JWT `ADMIN` ou `GERENTE`; Empresa derivada do principal.
- Entrada: somente `orderId` no path; não recebe valor nem empresa no body.
- Regra: reembolso integral de Order/PaymentAttempt `PROCESSED`, com ownership
  tenant-aware e lock pessimista.
- Saída `200`: `orderId`, `status=refunded`, `amount`, `requestedAt`,
  `confirmedAt` e `confirmed=true`.
- Erros: `401/403`, `404 PURCHASE_NOT_FOUND`, `409 REFUND_NOT_ALLOWED` e `502`
  sanitizado quando o resultado externo ainda não pôde ser confirmado.
- Idempotência: retry de Order já `REFUNDED` retorna sucesso e não repete os efeitos.
- Efeitos: Order/PaymentAttempt `REFUNDED`, estoque/cupom liberados, evento ao
  Terminal, auditoria e `REFUND_REVERSAL` único no ledger de fidelidade.

# Arquivos e documentação

## POST /files/upload

- Segurança: JWT `ADMIN` ou `GERENTE`.
- Entrada: multipart parameter `file`, extensão `.jpg`/`.jpeg` e conteúdo decodificável como imagem; limite global 30 MB.
- Resposta: URL textual relativa `/files/{nome}`.
- Efeito: grava arquivo no filesystem local.

## GET /files/{nome}

- Não é controller: resource handler de `WebConfig`.
- Segurança real: pública.
- Resposta: conteúdo de `uploads/` quando existente.

## GET /

- Controller: `DocController`.
- Segurança real: pública por matcher explícito.
- Resposta: redirect para `/swagger-ui/index.html`.

# Testes e debug — habilitados no ambiente de desenvolvimento

Somente quando `app.test-endpoints.enabled=true` (opt-in por `TEST_ENDPOINTS_ENABLED`;
o padrão é `false`) e fora do profile `prod`, os endpoints abaixo são registrados,
exigem JWT `ADMIN`/`GERENTE` e fazem chamadas reais à API usando
`MP_TEST_ACCESS_TOKEN`. São implementados por `TestarWebhookMPController`/
`TestarWebhookMP`. Com `@Profile("!prod")`, o controller nunca é registrado em `prod`,
mesmo com a propriedade ligada.

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

- WebSocket nativo autenticado no upgrade por `X-Terminal-Token`; origem vem de
  `WEBSOCKET_ALLOWED_ORIGINS`.
- Entrada: `{ "terminalId": "uuid", "status": "ONLINE|OFFLINE|MANUTENCAO" }`.
- Efeito: somente atualiza o Terminal do Principal. Payload divergente fecha com
  policy violation sem persistência; versão/revogação/estado são revalidados.
- Saída após persistência: `{ "type":"HEARTBEAT_ACK", "terminalId":"uuid", "status":"ONLINE", "lastPing":"ISO_LOCAL_DATE_TIME" }`.

## /payment-socket/{terminalId}

- WebSocket nativo autenticado; Principal e path precisam identificar o mesmo Terminal.
- Saída após commit: `type`, `terminalId`, `orderId`, `transactionId`, `status`, `mercadoPagoStatus`, `statusDetail`, `message` e o campo legado `paid`.
- O fluxo ativo de webhook publica somente quando o estado muda e seleciona a sessão pelo terminal do carrinho.

## /ws e /topic/payment/{terminalId}

- Handshake STOMP usa a mesma device credential e Principal do Terminal.
- Broker simples em memória; não há `@MessageMapping` de entrada.
- `PaymentSocketService` publica o mesmo `PaymentEvent` após commit em `/topic/payment/{terminalId}`.
- `SUBSCRIBE` só aceita o tópico do próprio Terminal; todo `SEND` é negado.

## Credenciais administrativas do Terminal

- `POST /terminais/{id}/credential`: cria e retorna a credencial uma única vez;
- `POST /terminais/{id}/credential/rotate`: invalida a anterior e retorna a nova;
- `DELETE /terminais/{id}/credential`: revoga individualmente.

A rota legada `POST /terminal/{id}/credential/migrate` e o token global
`TERMINAL_INTERNAL_TOKEN` foram removidos em 18/10/2026; a rota cai no `denyAll` (401).

As três rotas exigem JWT `ADMIN/GERENTE` e validam o tenant. A resposta bruta não
existe em GET/listagens.

## POST /pagamento/terminal/order/{orderId}/cancelamento

Exige `X-Terminal-Token`, recebe `terminalId` como query parameter e não recebe body.
Valida Order/Terminal, persiste `CANCELLING` e a chave idempotente antes da chamada
externa e devolve `PaymentStatusResponse`. `cancellationRequested=true` com status
intermediário não significa cancelamento confirmado; o cliente continua bloqueado e
reconciliando. Aprovação confirmada concorrente prevalece.

`PointPaymentResponse` e `PaymentStatusResponse` incluem `remoteOrderId` e `cancellationRequested`. Esses campos não carregam credenciais e não autorizam o cliente a chamar o Mercado Pago.

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
