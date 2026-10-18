# Autenticação e autorização

## Canal do Terminal — atualização 27/09/2026

As rotas operacionais de carrinho/pagamento/status, sync, telemetria, lifecycle,
comprovante, WebSockets nativos e STOMP exigem `X-Terminal-Token`. O valor operacional
é agora uma credencial individual aleatória cujo hash resolve exatamente um Terminal,
Empresa e Condomínio. A ativação por serial permanece pública como descoberta, mas
não devolve credencial nem autoriza operação. Veja [[security/terminal-authentication]].

`TERMINAL_INTERNAL_TOKEN` e a rota de migração foram removidos em 18/10/2026; revogação
e rotação são individuais. Uso da mesma credencial de origens diferentes gera alerta em
`audit_log` (ver [[security/terminal-authentication]]).

Voltar para [[00-index]]. Contexto multi-tenant em [[contexto]] e persistência em [[banco-de-dados]].

Os endpoints administrativos exigem JWT `ADMIN` ou `GERENTE`; `/app-cliente/**` e
`/user/**` exigem `ROLE_USER`; rotas operacionais do Terminal exigem
`ROLE_TERMINAL`. `SecurityConfig` usa uma allowlist pública explícita e termina em
`anyRequest().denyAll()`. Quando um JWT válido está
presente, `SecurityFilter` preenche `EmpresaContext`; sem contexto, o backend não
inventa empresa ou usuário.

## Pendência obrigatória de pré-produção

- [x] Ativar autenticação e autorização HTTP
- [x] Definir rotas públicas
- [x] Proteger as novas agregações `/admin/**`
- [x] Aplicar roles ADMIN/GERENTE nesse prefixo
- [ ] Migrar/proteger CRUDs administrativos legados fora de `/admin/**`
- [ ] Revisar CORS
- [ ] Desabilitar endpoints de teste/debug

## Credenciais e usuário

`User` implementa `UserDetails` e é a identidade única para usuários comuns e gestores. O identificador de login é o CPF (`getUsername`) e a senha persistida é `senha`. Gestores são usuários `ADMIN` ou `GERENTE` associados a uma empresa; não existe uma segunda entidade de autenticação.

O onboarding administrativo força o primeiro gestor a `ADMIN`. `POST /auth/register`
é o cadastro público do App Cliente: não aceita papel nem IDs de tenant, força `USER`
e cria a conta com Empresa/Condomínio nulos e CPF `UNVERIFIED`. O vínculo posterior
usa convite administrativo conforme [[app-cliente/account-tenant-link]].

Os papéis declarados são `ADMIN`, `GERENTE`, `PORTARIA`, `MORADOR` e `USER`. As authorities efetivas são:

- `ADMIN`: `ROLE_ADMIN`, `ROLE_GERENTE`, `ROLE_USER`;
- `GERENTE`: `ROLE_GERENTE`, `ROLE_USER`;
- qualquer outro papel: somente `ROLE_USER`.

`PORTARIA`, `MORADOR` e `USER` continuam recebendo somente `ROLE_USER`.

`User.isEnabled()` agora respeita `ativo`; uma conta com `ativo=false` é recusada pelo mecanismo de autenticação.

## Login e JWT

`POST /auth/login` autentica CPF e senha via `AuthenticationManager` e retorna `AuthResponse` com token e dados do usuário. `POST /auth/login/admin` faz a mesma autenticação e, adicionalmente, aceita somente `ADMIN` ou `GERENTE`.

O token:

- é assinado com HMAC256 usando a propriedade `api.secret.token`;
- usa o CPF como `subject`;
- inclui claims `userId` e `empresaId`;
- expira 200 horas após a emissão, calculadas com offset fixo `-03:00`;
- não declara issuer ou audience no código.

O segredo JWT não possui mais fallback literal: `JWT` é obrigatório fora do profile de teste. SMTP e credenciais de banco também vêm de variáveis de ambiente. Valores anteriormente versionados ainda precisam ser rotacionados nos provedores.

O filtro lê `Authorization`, remove literalmente `Bearer ` e valida assinatura/expiração uma única vez. O resultado validado contém `subject`, `userId` e `empresaId`. Os dois claims de identidade são comparados com o estado atual do banco antes de preencher o `SecurityContext` e o `EmpresaContext`; portanto um token antigo deixa de autenticar quando o usuário muda de Empresa ou quando a Empresa é desativada/encerrada.

Uma exceção deliberada permite que uma conta `USER` ainda global autentique com JWT
sem `empresaId` exclusivamente para concluir o vínculo. Depois que a Empresa é
persistida, esse token antigo não confere mais com o banco; o consumo retorna um JWT
novo com o tenant.

`UserRepository.findSecurityIdentityByCpf` usa uma projeção JPQL específica para o caminho crítico. A mesma consulta retorna o `User` usado como principal e apenas `Empresa.id`, `Empresa.ativo` e `Empresa.closed_at`. O filtro não chama getters da associação `User.empresa`, que permanece `LAZY`, e não depende de Open Session in View, `JOIN FETCH`, relacionamento `EAGER` ou transação aberta durante toda a requisição. Isso também evita uma segunda consulta e N+1 no filtro.

Em rota protegida, Empresa desativada/encerrada produz `403` com código `COMPANY_DISABLED`; divergência ou ausência da Empresa informada no JWT produz `COMPANY_NOT_FOUND`. Usuário desativado produz `USER_DISABLED`. Uma rota `permitAll` continua atravessando a cadeia mesmo quando recebe um JWT antigo sem identidade operacional válida. Tokens criptograficamente inválidos continuam seguindo o comportamento anterior de `TokenService.validate`, que lança `RuntimeException`; esta correção não alterou o contrato geral de JWT inválido.

## Política HTTP atual

As rotas `/app-cliente/**` exigem `ROLE_USER` (papel concedido também a ADMIN e
GERENTE). Os consumidores de QR `/terminal/customer-link/consume` e
`/terminal/coupons/apply` não usam o JWT do cliente: exigem a device credential em
`X-Terminal-Token` e validam Terminal, Empresa e Condomínio.

`POST /admin/app-cliente/tenant-invitations` fica sob `/admin/**` e exige
`ADMIN/GERENTE`. `POST /app-cliente/tenant-link` exige `USER`; seu body contém apenas
o token opaco, nunca IDs de tenant.

`SecurityConfig` desabilita CSRF, define sessão stateless, executa os filtros JWT e
Terminal antes de `UsernamePasswordAuthenticationFilter`, restringe os grupos por
papel e nega rotas não classificadas. Não há
`@PreAuthorize`. Todos os services de agregação ainda exigem
`EmpresaContext.require()` e todas as queries filtram a Empresa autenticada.

CORS aceita padrões de origem, métodos, headers e headers expostos com curinga e permite credenciais.

## Contexto de empresa

Quando um JWT válido identifica usuário e Empresa ativos, `SecurityFilter` coloca o `empresaId` escalar da projeção em `EmpresaContext`, baseado em `ThreadLocal`, e o remove em `finally` após a cadeia do filtro. A limpeza ocorre também se um controller, interceptor ou outro componente posterior lançar exceção, impedindo vazamento de tenant entre requisições reutilizadas pela mesma thread.

O contexto é obrigatório nas APIs administrativas de empresa, condomínio e terminal. `EmpresaContext.require()` devolve `401` quando a identidade não fornece empresa. Os repositories validam o tenant na consulta:

- condomínio: `findByIdCondominioAndEmpresaId`;
- terminal: `findByIdTerminalAndCondominioEmpresaId`, atravessando condomínio e empresa;
- listagens incluem a empresa autenticada.

Não há filtro Hibernate global. As consultas legadas por ID, CPF ou código continuam sem restrição automática por tenant.

## Recuperação de senha

O fluxo usa `PasswordRecoveryService`: resposta de solicitação é idêntica para CPF
existente/inexistente; código e token são aleatórios, armazenados somente por HMAC no
Redis, expiram e são consumidos atomicamente uma única vez. Limites configuráveis
combinam hash de CPF normalizado e origem e retornam 429. A senha final usa BCrypt.
Detalhes em [[security/password-recovery-security]].

`POST /user/alterar-senha` exige JWT e o `userId` deve ser exatamente o principal. A nova senha tem 8 a 72
caracteres e precisa ser diferente da atual.

`PUT /user/{id}`: gestor não altera e-mail de terceiros e GERENTE não altera ADMIN. O e-mail é o destino
da recuperação de senha; permitir a troca por terceiros permitia tomar a conta (ver [[api]]).

## Validação de e-mail

`POST /auth/send-code` gera código de seis dígitos, armazena-o em `email_validation_queue:{email}` por 15 minutos e coloca o DTO na lista `email_validation_queue`. O worker envia o e-mail; `nome`, `email` e código entram no HTML com escape. Os dois endpoints validam o corpo
(`ValidateEmailRequest.SendCode`/`VerifyCode`). `POST /auth/validate-code` compara e remove o código e, quando existe usuário com o e-mail informado, marca `User.emailVerificado` na mesma operação transacional.

## Cadastro e e-mail de boas-vindas

Após persistir um usuário, `UserService` publica `UserCreatedEvent`. `UserListener` processa o evento com `@Async` e envia e-mail de boas-vindas diretamente via JavaMail. Diferentemente das outras mensagens, esse e-mail não passa por Redis.

## Pontos sensíveis observados

- A allowlist pública completa e sua justificativa estão em
  [[security/public-endpoints]]. CRUDs gestores fora de `/admin/**` são mapeados
  explicitamente para `ADMIN`/`GERENTE` e continuam tenant-aware no serviço.
- `User.isEnabled()` respeita o campo `ativo`; somente `Boolean.TRUE` mantém a conta habilitada.
- As propriedades sensíveis atuais foram externalizadas. Valores que já existiram no repositório devem ser considerados expostos e ainda precisam de rotação fora do código.
- A propriedade de segredo JWT e credenciais SMTP/Mercado Pago estão configuradas por propriedades; valores não são reproduzidos aqui.
- O JWT tem validade extensa de 200 horas e não há refresh token ou revogação.
- Token inválido ou expirado não derruba a requisição: `SecurityFilter` segue anônimo com código
  `TOKEN_INVALID`; rota protegida responde `401 {code: TOKEN_INVALID}` e rota pública funciona normalmente
  (antes a exceção escapava do filtro e virava `500`).
- Estado OAuth é temporário no Redis, validado antes da busca do usuário e removido após troca bem-sucedida; veja [[mercado-pago]].
