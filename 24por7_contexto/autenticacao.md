# Autenticação e autorização

Voltar para [[00-index]]. Contexto multi-tenant em [[contexto]] e persistência em [[banco-de-dados]].

> Os endpoints agregados sob `/admin/**` já exigem JWT com papel `ADMIN` ou
> `GERENTE`. Diversas rotas legadas fora desse prefixo permanecem temporariamente
> desprotegidas e ainda precisam de revisão antes da produção.

`SecurityConfig` aplica primeiro
`requestMatchers("/admin/**").hasAnyRole("ADMIN", "GERENTE")` e mantém
`anyRequest().permitAll()` apenas como regra residual. Quando um JWT válido está
presente, `SecurityFilter` preenche `EmpresaContext`; sem contexto, o backend não
inventa empresa ou usuário.

## Pendência obrigatória de pré-produção

- [ ] Ativar autenticação e autorização HTTP
- [ ] Definir rotas públicas
- [x] Proteger as novas agregações `/admin/**`
- [x] Aplicar roles ADMIN/GERENTE nesse prefixo
- [ ] Migrar/proteger CRUDs administrativos legados fora de `/admin/**`
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

O filtro lê `Authorization`, remove literalmente `Bearer ` e valida assinatura/expiração uma única vez. O resultado validado contém `subject`, `userId` e `empresaId`. Os dois claims de identidade são comparados com o estado atual do banco antes de preencher o `SecurityContext` e o `EmpresaContext`; portanto um token antigo deixa de autenticar quando o usuário muda de Empresa ou quando a Empresa é desativada/encerrada.

`UserRepository.findSecurityIdentityByCpf` usa uma projeção JPQL específica para o caminho crítico. A mesma consulta retorna o `User` usado como principal e apenas `Empresa.id`, `Empresa.ativo` e `Empresa.closed_at`. O filtro não chama getters da associação `User.empresa`, que permanece `LAZY`, e não depende de Open Session in View, `JOIN FETCH`, relacionamento `EAGER` ou transação aberta durante toda a requisição. Isso também evita uma segunda consulta e N+1 no filtro.

Em rota protegida, Empresa desativada/encerrada produz `403` com código `COMPANY_DISABLED`; divergência ou ausência da Empresa informada no JWT produz `COMPANY_NOT_FOUND`. Usuário desativado produz `USER_DISABLED`. Uma rota `permitAll` continua atravessando a cadeia mesmo quando recebe um JWT antigo sem identidade operacional válida. Tokens criptograficamente inválidos continuam seguindo o comportamento anterior de `TokenService.validate`, que lança `RuntimeException`; esta correção não alterou o contrato geral de JWT inválido.

## Política HTTP atual

`SecurityConfig` desabilita CSRF, define sessão stateless, executa o filtro JWT
antes de `UsernamePasswordAuthenticationFilter`, restringe `/admin/**` a
`ADMIN`/`GERENTE` e libera as demais requisições pela regra residual. Não há
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

- As agregações `/admin/**` possuem proteção HTTP; empresa, condomínio, produto
  e outros CRUDs legados fora desse prefixo ainda dependem das validações
  internas de tenant quando há contexto.
- `User.isEnabled()` respeita o campo `ativo`; somente `Boolean.TRUE` mantém a conta habilitada.
- As propriedades sensíveis atuais foram externalizadas. Valores que já existiram no repositório devem ser considerados expostos e ainda precisam de rotação fora do código.
- A propriedade de segredo JWT e credenciais SMTP/Mercado Pago estão configuradas por propriedades; valores não são reproduzidos aqui.
- O JWT tem validade extensa de 200 horas e não há refresh token ou revogação.
- Estado OAuth é temporário no Redis, validado antes da busca do usuário e removido após troca bem-sucedida; veja [[mercado-pago]].
