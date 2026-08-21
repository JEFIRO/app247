# Autenticação e autorização

Voltar para [[00-index]]. Contexto multi-tenant em [[contexto]] e persistência em [[banco-de-dados]].

> As rotas HTTP permanecem temporariamente desprotegidas durante a fase de desenvolvimento e testes. Essa é uma decisão temporária e não representa a configuração desejada para produção.

`SecurityConfig` aplica uma única regra `anyRequest().permitAll()`: não há matcher administrativo exigindo JWT ou role nesta fase. Quando um JWT válido está presente, `SecurityFilter` continua preenchendo `EmpresaContext`; sem contexto, o backend não inventa empresa ou usuário.

## Pendência obrigatória de pré-produção

- [ ] Ativar autenticação e autorização HTTP
- [ ] Definir rotas públicas
- [ ] Proteger rotas administrativas
- [ ] Aplicar roles ADMIN/GERENTE
- [ ] Revisar CORS
- [ ] Desabilitar endpoints de teste/debug

## Credenciais e usuário

`User` implementa `UserDetails` e é a identidade única para usuários comuns e gestores. O identificador de login é o CPF (`getUsername`) e a senha persistida é `senha`. Gestores são usuários `ADMIN` ou `GERENTE` associados a uma empresa; não existe uma segunda entidade de autenticação.

O onboarding força o primeiro gestor a `ADMIN`, ignorando qualquer papel recebido no DTO. Embora `POST /auth/register` esteja público na camada HTTP, o caso de uso exige gestor/contexto para derivar a empresa e força o novo cadastro a `USER`; o cliente não pode promover a si mesmo por `roleUser`.

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

O filtro lê `Authorization`, remove literalmente `Bearer `, valida assinatura/expiração, busca o usuário pelo CPF, preenche o `SecurityContext` e define a empresa quando existente. Ele não registra mais token ou usuário no console. Tokens inválidos ainda fazem `TokenService.validate` lançar `RuntimeException`; não há tratamento específico no filtro.

## Política HTTP atual

`SecurityConfig` desabilita CSRF, define sessão stateless, executa o filtro JWT antes de `UsernamePasswordAuthenticationFilter` e libera todas as requisições com `anyRequest().permitAll()`. Não há `@PreAuthorize`. Services que obrigatoriamente precisam de tenant continuam exigindo `EmpresaContext`; a rota aberta não fornece nem inventa esse contexto.

CORS aceita padrões de origem, métodos, headers e headers expostos com curinga e permite credenciais.

## Contexto de empresa

Quando um JWT válido identifica um usuário, `SecurityFilter` coloca `user.empresa.id` em `EmpresaContext`, baseado em `ThreadLocal`, e o remove em `finally` após a cadeia do filtro.

O contexto é obrigatório nas APIs administrativas de empresa, condomínio e terminal. `EmpresaContext.require()` devolve `401` quando a identidade não fornece empresa. Os repositories validam o tenant na consulta:

- condomínio: `findByIdCondominioAndEmpresaId`;
- terminal: `findByIdTerminalAndCondominioEmpresaId`, atravessando condomínio e empresa;
- listagens incluem a empresa autenticada.

Não há filtro Hibernate global. As consultas legadas por ID, CPF ou código continuam sem restrição automática por tenant.

## Recuperação de senha

1. `POST /user/recuperar` recebe CPF.
2. `UserService` busca o usuário, gera código numérico de seis dígitos com `java.util.Random`, grava `recovery:{cpf}` por 15 minutos e enfileira o DTO em `recovery_queue`.
3. `EmailWorker`, a cada dois segundos, consome e envia e-mail HTML; em falha, recoloca o item na fila.
4. `POST /user/validar` compara o código, apaga a chave de recuperação e cria `reset:{cpf}` com UUID e TTL de 15 minutos.
5. `POST /user/redefinir-senha` valida o token, grava nova senha BCrypt e apaga a chave.

Embora o controller responda com mensagem neutra para o pedido de recuperação, o service transforma falhas em `RuntimeException`; portanto CPF inexistente pode resultar em erro HTTP e revelar diferença de comportamento.

`POST /user/alterar-senha` recebe `userId`, senha atual e nova senha. Ele não deriva o usuário do principal autenticado e, como a rota é pública, a autorização depende apenas de conhecer o ID e a senha atual.

## Validação de e-mail

`POST /auth/send-code` gera código de seis dígitos, armazena-o em `email_validation_queue:{email}` por 15 minutos e coloca o DTO na lista `email_validation_queue`. O worker envia o e-mail. `POST /auth/validate-code` compara e remove o código e, quando existe usuário com o e-mail informado, marca `User.emailVerificado` na mesma operação transacional.

## Cadastro e e-mail de boas-vindas

Após persistir um usuário, `UserService` publica `UserCreatedEvent`. `UserListener` processa o evento com `@Async` e envia e-mail de boas-vindas diretamente via JavaMail. Diferentemente das outras mensagens, esse e-mail não passa por Redis.

## Pontos sensíveis observados

- Não existe proteção HTTP administrativa nesta fase; empresa, condomínio e terminal dependem das validações internas de tenant quando há contexto.
- `User.isEnabled()` respeita o campo `ativo`; somente `Boolean.TRUE` mantém a conta habilitada.
- As propriedades sensíveis atuais foram externalizadas. Valores que já existiram no repositório devem ser considerados expostos e ainda precisam de rotação fora do código.
- A propriedade de segredo JWT e credenciais SMTP/Mercado Pago estão configuradas por propriedades; valores não são reproduzidos aqui.
- O JWT tem validade extensa de 200 horas e não há refresh token ou revogação.
- Estado OAuth é temporário no Redis, validado antes da busca do usuário e removido após troca bem-sucedida; veja [[mercado-pago]].
