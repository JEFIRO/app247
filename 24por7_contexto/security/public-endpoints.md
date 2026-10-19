# Endpoints públicos

`SecurityConfig` termina em `denyAll`; somente as exceções abaixo são públicas.

| Método | Endpoint | Motivo | Rate limit / proteção | Observação |
|---|---|---|---|---|
| POST | `/auth/login`, `/auth/login/admin` | entrada de autenticação | falhas por CPF (5/15 min) + tentativas por origem (50/15 min) | não revela senha |
| POST | `/auth/register` | cadastro do App Cliente | limitador próprio | cria conta global `USER` |
| POST | `/auth/send-code`, `/auth/validate-code` | validação de e-mail | TTL, validação de corpo, escape HTML, limite por e-mail/origem e tentativas por e-mail | código invalidado ao esgotar tentativas |
| POST | `/user/recuperar`, `/user/validar`, `/user/redefinir-senha` | recuperação | CPF+origem, TTL, uso único | resposta inicial anti-enumeração |
| POST | `/onboarding`, `/condominio` | bootstrap inicial | validação transacional + 5 por origem/hora | `/condominio` é alias legado |
| GET | `/terminal/serial/{serial}` | descoberta inicial | não autentica operação | só `terminalId`, `serialNumber`, `ativo`, `activated` |
| POST | `/webhook/mercadopago` | callback externo | assinatura/validação do provedor | idempotente |
| GET | `/public/app-config` | configuração pública | leitura | sem segredo |
| POST | `/public/leads` | captação pública | proteção própria | dados validados |
| GET | `/`, `/actuator/health`, `/terminal/health` | documentação/saúde | sem estado sensível | health mínimo |
| GET | `/v3/api-docs/**`, `/swagger-ui/**` | contrato OpenAPI | somente leitura | pode ser desabilitado por ambiente |
| ANY | `/error` | dispatch interno de erro | sem controller de negócio | evita mascarar 404/500 |

Limites configuráveis em `app.security.login.*`, `app.security.email-code.*` e `app.security.onboarding.*`
(variáveis `LOGIN_*`, `EMAIL_CODE_*`, `ONBOARDING_*`), aplicados por `PublicAuthRateLimits` sobre
`SecurityRateLimiter` (Redis com chave HMAC; sem Redis, limite local por instância). O limite por origem
usa `getRemoteAddr()`, que com `forward-headers-strategy=framework` reflete `X-Forwarded-For` e só é
confiável atrás de proxy que sobrescreva esse header.

A migração com token global (`/terminal/{id}/credential/migrate`) foi removida em 18/10/2026.

Mesmo quando o matcher HTTP permite bootstrap ou callback, o caso de uso
ainda valida seus controles próprios. Carrinho, Order, checkout, comprovante,
telemetria, sync e QR operacional não são públicos.
