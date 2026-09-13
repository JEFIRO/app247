# Auditoria de negócio

`audit_log` é append-only e separado de logs técnicos.

Nesta baseline, Produto, promoção e entrada/ajuste manual de estoque já gravam auditoria na mesma transação do negócio. A estrutura também foi desenhada para configuração de Terminal/Point, configuração Mercado Pago e reconciliação administrativa excepcional; esses pontos devem chamar o mesmo serviço quando seus fluxos administrativos forem ampliados.

Campos: tenant, tipo/ID do ator, usuário/Terminal opcionais, ação, tipo/ID da entidade, correlation ID, estado anterior/posterior, metadata e `created_at` UTC.

Antes de persistir, o serviço remove chaves sensíveis recursivamente: senha, token, authorization, JWT, segredo, PSK e credenciais de pagamento. O registro antigo não é atualizado nem apagado pelo fluxo operacional.

Stacktraces, timeouts HTTP e debug permanecem no logging técnico com retenção externa de 30–90 dias; arquivos de log não são copiados para `audit_log`.
