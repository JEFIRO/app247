# Retenção de dados

| Dado | Retenção mínima/alvo | Limpeza automática |
|---|---:|---|
| Order e OrderItem | 5 anos | não nesta fase |
| PaymentAttempt e PaymentEvent | 5 anos | não |
| MovimentacaoEstoque | 5 anos | não |
| WebhookEvent sanitizado | 1–5 anos, padrão 365 dias | configurável |
| AuditLog | 5 anos para ações financeiras; demais conforme política | não por padrão |
| Vínculos Mercado Pago e Point | junto ao histórico financeiro/auditoria aplicável | não |
| Telemetria detalhada | 30–180 dias, padrão 30 | `TERMINAL_TELEMETRY_RETENTION_DAYS` |
| Alertas operacionais resolvidos | 6–24 meses, padrão 365 dias | configurável |
| Logs técnicos | 30–90 dias | fora do MySQL/aplicação |

Jobs de retenção operam somente nas tabelas explicitamente permitidas. Nenhum job genérico alcança `orders`, `order_item`, `payment_attempt`, `payment_event` ou `movimentacao_estoque`.

Unlink Mercado Pago e factory reset da aplicação não removem esses registros do
backend. Eles encerram os vínculos ativos e conservam Orders, tentativas,
eventos, IDs do gateway, histórico Point e AuditLog.

Uma futura agregação horária/diária de telemetria poderá preceder a remoção das amostras detalhadas; ela não faz parte desta baseline.
