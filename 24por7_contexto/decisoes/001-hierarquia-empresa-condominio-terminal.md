# ADR 001 — Hierarquia Empresa, Condomínio e Terminal

Voltar para [[00-index]]. Contexto relacionado em [[contexto]], [[arquitetura]], [[autenticacao]] e [[banco-de-dados]].

## Status

Aceita e implementada.

## Contexto

O modelo mantinha `empresa_id` e `id_condominio` no terminal. O onboarding de gestor, empresa, condomínio e terminal estava em `CondominioService`, e as APIs administrativas faziam consultas globais. Isso permitia inconsistência entre a empresa direta do terminal e a empresa do condomínio, misturava cadastro inicial com manutenção de condomínio e não estabelecia isolamento por tenant.

## Decisão

```text
Empresa 1
 |
 +--- N Condomínios
        |
        +--- N Terminais
```

- `Condominio` possui `empresa_id` obrigatório.
- `Terminal` possui somente `condominio_id` obrigatório.
- A empresa do terminal é sempre `terminal.condominio.empresa`.
- As cardinalidades são consultadas por repositories; não foram adicionadas árvores bidirecionais.
- `User` continua sendo a única identidade autenticável. `ADMIN` e `GERENTE` representam gestores associados à empresa.
- `OnboardingService` coordena a criação inicial em uma transação.
- `CondominioService` e `TerminalService` tratam a manutenção posterior de seus recursos.
- APIs administrativas derivam o tenant do JWT por `EmpresaContext` e consultam recurso + empresa na mesma query.

## Contratos

O onboarding principal é `POST /onboarding`. O nome JSON do usuário inicial passou a ser `gestor`; o alias `user` permanece aceito. `POST /condominio` permanece temporariamente como alias, atendido por `OnboardingController`.

As APIs `/empresas/**`, `/condominios/**` e `/terminais/**` estão públicas temporariamente na camada HTTP. Os casos de uso continuam usando a empresa do contexto quando obrigatória; `POST /auth/register` força papel `USER` e não aceita promoção pelo payload.

## Consequências

- Não é possível persistir um terminal ligado simultaneamente a empresas divergentes.
- Uma empresa pode receber novos condomínios e cada condomínio novos terminais sem reutilizar o onboarding.
- Operações administrativas com IDs de outro tenant retornam recurso não encontrado.
- A migration V18 é necessária antes de iniciar a aplicação com o novo mapeamento.
- A autorização das áreas legadas não foi ampliada por esta decisão.
