# Leads do site comercial

Voltar para [[00-index]].

## Escopo atual

O backend expõe `POST /public/leads` para receber contatos do site comercial sem utilizar APIs administrativas autenticadas. A rota aceita dados de contato, contexto básico da operação, UTMs e página de entrada.

Cada envio válido cria um registro independente em `marketing_lead` com status inicial `NEW`. O mesmo e-mail ou telefone pode gerar novos contatos; não há constraint de unicidade e não foi criado um CRM nesta etapa.

## Proteções

- Bean Validation, limites de tamanho no DTO e rejeição antecipada de corpos acima de 16 KB;
- sanitização de espaços e caracteres de controle antes da persistência;
- honeypot `website`, confirmado sem persistência quando preenchido;
- rate limiting por IP e e-mail no Redis, com fallback local se o Redis estiver indisponível;
- nenhuma credencial, token ou payload arbitrário é armazenado.

O rate limit atual permite até 30 solicitações por IP e 6 por e-mail a cada 30 minutos. Em ambiente com múltiplas réplicas, o Redis mantém a contagem compartilhada.

## Persistência

A migration `V13__marketing_leads.sql` cria a tabela e índices para consulta por status/data, e-mail/data e telefone/data. Os estados previstos são:

- `NEW`
- `CONTACTED`
- `QUALIFIED`
- `WON`
- `LOST`

## Evolução futura

O painel Flutter poderá adicionar uma área `Leads` para listar, filtrar e atualizar status. Essa interface, regras de atribuição, histórico de contatos e automações comerciais não fazem parte desta implementação.
