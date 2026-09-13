# Ciclo de vida da conta Mercado Pago

Voltar para [[00-index]]. Fluxo de pagamento em [[mercado-pago]], ciclo do equipamento em [[terminal-lifecycle]] e decisão original de Point em [[decisoes/002-credenciais-e-terminais-mercado-pago]].

## Decisão de modelo

`MercadoPagoConta` continua sendo o vínculo histórico entre uma Empresa e uma autorização OAuth. Não foi criada uma segunda cópia da integração nem uma entidade externa especulativa. Cada autorização inicial ou substituição cria uma linha histórica; reautorização da mesma conta na mesma Empresa atualiza a linha ativa.

`mercado_pago_conta_ativa` é uma projeção pequena e sem tokens que representa exclusivamente a posse ativa:

- `mp_user_id` é PK;
- `empresa_id` é UNIQUE;
- `mercado_pago_conta_id` é UNIQUE;
- a FK composta garante que conta histórica, Empresa e identidade externa coincidem.

A identidade externa é o `user_id` devolvido pelo endpoint OAuth `/oauth/token`, já mapeado por `MercadoPagoTokenResponse.user_id` e persistido como `mp_user_id`. Não se usa Point, nome, e-mail nem um ID inferido para identificar a conta autorizada.

## Invariantes

1. Uma identidade `mp_user_id` tem no máximo um lease ativo no sistema.
2. Uma Empresa tem no máximo uma conta ativa.
3. `mercado_pago_conta` não possui UNIQUE global em `mp_user_id`: o histórico A/UNLINKED e B/ACTIVE é permitido.
4. Somente o vínculo `ACTIVE`, com token preenchido e não expirado, pode fornecer credenciais.
5. Uma Point operacional precisa de `TerminalPointBinding ACTIVE` da mesma conta ativa da Empresa.
6. Unlink ou substituição não apagam Orders, PaymentAttempts, PaymentEvents, IDs externos nem AuditLog.

## OAuth

O state no Redis guarda `userId|empresaId|replace` por dez minutos. O callback valida novamente usuário, papel e Empresa antes de trocar o code.

Após o token exchange:

- conta sem lease ativo: cria `MercadoPagoConta ACTIVE` e o lease;
- mesma conta na mesma Empresa: atualiza tokens/expiração e registra `MERCADO_PAGO_REAUTHORIZED`, sem duplicar histórico;
- conta ativa em outra Empresa: aborta sem alterar o vínculo vencedor e responde `409 MERCADO_PAGO_ACCOUNT_ALREADY_LINKED`, sem informar o outro tenant;
- Empresa com outra conta ativa: responde `409 MERCADO_PAGO_ACCOUNT_REPLACEMENT_REQUIRED` se `replace=false`;
- substituição confirmada: bloqueia a Empresa, exige ausência de pagamentos não resolvidos, encerra Points e vínculo antigos, cria a nova conta e deixa todas as Points não configuradas.

O token recebido em uma tentativa rejeitada não é persistido. Authorization code, access token, refresh token e client secret não entram no AuditLog nem em mensagens de erro.

## Concorrência

O lock pessimista da Empresa serializa callbacks da mesma Empresa. A disputa entre Empresas diferentes pela mesma conta é decidida pela PK de `mercado_pago_conta_ativa.mp_user_id`.

`MercadoPagoContaAtiva` implementa `Persistable` e marca uma instância recém-criada como nova, obrigando `persist/INSERT`. Isso é deliberado: `merge/UPDATE` poderia trocar silenciosamente a Empresa dona do lease. O callback captura `DataIntegrityViolationException`, reavalia se a operação concorrente foi uma reautorização da mesma Empresa e, nos demais casos, converte para `MERCADO_PAGO_ACCOUNT_ALREADY_LINKED`. Erro SQL cru não atravessa a API.

## Unlink e reutilização

`DELETE /mercado-pago/conta`:

1. bloqueia a Empresa;
2. recusa enquanto houver `PaymentAttempt PENDING` ou `ACTION_REQUIRED`;
3. muda o vínculo histórico de `ACTIVE` para `UNLINKED`;
4. preenche `unlinked_at` e motivo;
5. limpa access token, refresh token, public key, scope, token type e datas do token;
6. encerra todos os `TerminalPointBinding ACTIVE` da conta e limpa `Terminal.mercadoPagoTerminalId`;
7. remove somente o lease ativo.

O Terminal continua ativado, na mesma Empresa/Condomínio, com seu UUID, catálogo e sincronização. O resultado operacional é `PAYMENT_NOT_CONFIGURED`. Como o lease foi removido, a mesma `mp_user_id` pode ser autorizada por outra Empresa em um novo vínculo histórico.

## Point e pagamento

`terminal_point_binding` preserva Terminal, Empresa, vínculo histórico da conta, Point, estado e intervalo `linked_at/unlinked_at`. A coluna `Terminal.mercadoPagoTerminalId` permanece como projeção operacional compatível com os consumidores existentes.

Antes de criar Order/PaymentAttempt e novamente antes do HTTP para o Mercado Pago, `MercadoPagoOperationalConfigurationService` exige:

- Empresa operacional;
- Terminal `ACTIVE` da mesma Empresa;
- lease de conta ativo e credencial utilizável;
- Point ativa ligada ao mesmo vínculo histórico da conta;
- projeção `mercadoPagoTerminalId` igual à Point histórica ativa.

Qualquer divergência produz `MERCADO_PAGO_NOT_CONFIGURED` e a API externa não é chamada.

A consulta usada pelo bootstrap do Terminal é deliberadamente não excepcional: ausência de
lease, token expirado ou Point incompatível retorna `paymentConfigured=false`. Ela não chama
o caminho `requireConfigured`, porque uma exceção de negócio atravessando um service
transacional marcaria a transação de leitura como rollback-only mesmo quando capturada pelo
bootstrap. O caminho de cobrança continua usando `requireConfigured` e retorna
`MERCADO_PAGO_NOT_CONFIGURED` quando a configuração é obrigatória.

## Estado administrativo

`GET /mercado-pago/status` preserva os booleanos existentes e acrescenta `contaStatus`: `CONTA_NAO_VINCULADA`, `CONTA_VINCULADA`, `CONTA_VINCULADA_SEM_POINT`, `POINT_CONFIGURADA`, `CONTA_REVOGADA` ou `CONTA_COM_ERRO`.

O checklist usa os mesmos dados: lease ausente significa Mercado Pago ❌ e Point ❌; nova conta sem Point significa Mercado Pago ✅ e Point ❌.

## Auditoria

Os fluxos registram `MERCADO_PAGO_LINKED`, `MERCADO_PAGO_REAUTHORIZED`, `MERCADO_PAGO_UNLINKED`, `MERCADO_PAGO_REPLACED`, `MERCADO_PAGO_ACCOUNT_LINK_CONFLICT`, `POINT_LINKED` e `POINT_UNLINKED`. Metadados contêm apenas IDs operacionais não secretos.
