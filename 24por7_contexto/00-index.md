# App 24/7 — documentação técnica

Esta base descreve o backend conforme o código presente no repositório. O código executável e as migrations são a fonte primária; limitações e divergências encontradas são registradas nos documentos, sem pressupor funcionalidades ainda não implementadas.

## Visão geral

- [[contexto]] — objetivo observado, capacidades, clientes e limites do sistema.
- [[arquitetura]] — packages, camadas, componentes e fluxos principais.
- [[arquitetura-geral]] — visão transversal da arquitetura, integrações e fluxos executáveis.
- [[mapa-projeto]] — mapa dos packages e responsabilidades reais das classes.
- [[api]] — catálogo completo das rotas e matriz da segurança atualmente configurada.
- [[client-contracts]] — correspondência entre DTOs backend, models Flutter e cache/estado do Terminal.
- [[auditoria-resumo]] — estado executivo, principais riscos e resultado das verificações.

## Infraestrutura e dados

- [[database]] — schema reconstruído, entidades, constraints multi-tenant e nova baseline Flyway.
- [[database-er]] — diagrama ER do schema final.
- [[database-retention]] — retenção de vendas, pagamentos, estoque, telemetria, webhooks e logs.
- [[financial-rules]] — precisão `DECIMAL(15,6)`, `BigDecimal` e arredondamento do total cobrado.
- [[fiscal-future]] — preparação de Produto para futura NFC-e, sem implementar emissão.
- [[audit-log]] — auditoria de negócio append-only e sanitização de dados sensíveis.
- [[database-atual]] e [[database-melhorias]] — auditoria histórica da cadeia V1–V26 removida; não representam o schema executável atual.
- [[banco-de-dados]] — inventário legado anterior à reconstrução, mantido somente como referência histórica.
- [[estoque]] — estoque central, estoque por condomínio, transferências, planograma, movimentações, concorrência e ciclo da venda.
- [[inventario]] — contagem física, modo cego, conflitos, idempotência e ajustes no ledger.
- [[importacao-produtos]] — modelo XLSX, validação/preview, job assíncrono e estoque inicial.
- [[autenticacao]] — Spring Security, JWT, papéis, recuperação de senha e contexto de empresa.
- [[websocket]] — endpoints WebSocket nativos, STOMP e eventos de pagamento.
- [[terminal-lifecycle]] — estados duráveis, descoberta offline e factory reset da aplicação.
- [[sincronizacao-produtos]] — invalidação WebSocket e sync full/incremental do catálogo por Terminal.
- [[promocoes]] — modelo, cálculo, abrangência, checkout, sync e transições temporais.
- [[regras-financeiras]] — precisão monetária e arredondamento do total cobrado.
- [[telemetria]] — saúde do Raspberry/Terminal Python, presença, histórico e alertas.
- [[admin-dashboard]] — agregações administrativas, KPIs, checklist, atividade recente e Home Flutter.
- [[marketing-leads]] — endpoint público, validação, proteção contra abuso, persistência e evolução futura dos contatos comerciais.
- [[flutter-ui-audit]] — diagnóstico da interface administrativa e plano incremental.
- [[flutter-backend-gap]] — matriz de paridade real entre backend e Flutter.
- [[flutter-design-system]] — tema, tokens, componentes e breakpoints.
- [[flutter-white-label]] — estratégia de branding e fallback.
- [[flutter-web]] — execução, build, routing, autenticação e deploy Web.

## Decisões arquiteturais

- [[decisoes/001-hierarquia-empresa-condominio-terminal]] — hierarquia operacional, gestores e onboarding.
- [[decisoes/002-credenciais-e-terminais-mercado-pago]] — separação entre OAuth da empresa e maquininha física.
- Sempre use @Autowired para injeção de dependências
## Integrações

- [[mercado-pago]] — OAuth, Point Orders, webhook, filas Redis e fluxos de pagamento.
- [[mercado-pago-account-lifecycle]] — exclusividade ativa, histórico, reutilização, unlink e substituição de conta/Point.
- [[payment-reconciliation]] — recuperação após backend/webhook offline, endpoint, scheduler, startup e idempotência.
- [[payment-recovery]] — regra ponta a ponta contra cobrança duplicada, máquina de estados e roteiro operacional com Point offline.

## Auditoria e evolução

- [[auditoria-bugs]] — bugs confirmados e riscos potenciais com evidências no código.
- [[melhorias]] — plano priorizado, dívida técnica, qualidade da API e cobertura de testes.

## Escopo desta documentação

O schema executável é definido por treze migrations (`V1` a `V13`). A V11 adiciona o branding dinâmico da Empresa, a V12 adiciona os ciclos de vida de conta Mercado Pago, Point, Empresa e Terminal, e a V13 adiciona leads comerciais públicos. A cadeia histórica V1–V26 foi removida do caminho ativo e exige recriação do banco de desenvolvimento.
