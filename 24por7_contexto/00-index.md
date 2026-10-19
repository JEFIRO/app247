# App 24/7 — documentação técnica

Esta base descreve o backend conforme o código presente no repositório. O código executável e as migrations são a fonte primária; limitações e divergências encontradas são registradas nos documentos, sem pressupor funcionalidades ainda não implementadas.

## Visão geral

- [[contexto]] — objetivo observado, capacidades, clientes e limites do sistema.
- [[arquitetura]] — packages, camadas, componentes e fluxos principais.
- [[arquitetura-geral]] — visão transversal da arquitetura, integrações e fluxos executáveis.
- [[mapa-projeto]] — mapa dos packages e responsabilidades reais das classes.
- [[api]] — catálogo completo das rotas e matriz da segurança atualmente configurada.
- [[client-contracts]] — correspondência entre DTOs backend, models Flutter e cache/estado do Terminal.
- [[app-cliente]] — fachada móvel autenticada, catálogo, compras, fidelidade, cupons, QRs e consumidores do Terminal.
- [[app-cliente/account-tenant-link]] — vínculo autorizado de conta, Empresa e Condomínio.
- [[app-cliente/cpf-verification]] — estados e prova de verificação do CPF.
- [[app-cliente/historical-purchase-claim]] — claim tenant-aware e ausência de fidelidade retroativa.
- [[app-cliente/identity-hash-secret]] — configuração, fail-fast e limite de rotação do HMAC.
- [[app-cliente/customer-terminal-link]] — QR opaco, consumo single-use, vínculo Carrinho→Order e fidelidade.
- [[app-cliente/integration-tests]] — matriz de integração, security, tenant e ownership.
- [[app-cliente/p1b-audit]] — auditoria de configuração, fidelidade, recompensas e cupons antes/depois do P1-B.
- [[app-cliente/app-client-config]] — configuração e feature flags por Empresa.
- [[app-cliente/loyalty-config]] — modos `NONE`, `POINTS`, `CASHBACK` e invariantes.
- [[app-cliente/rewards]] — CRUD gestor, validações, elegibilidade e tenant.
- [[app-cliente/coupons]] — snapshot financeiro e constraints cross-tenant.
- [[app-cliente/security]] — roles, resolução de Empresa, defesa no banco e OpenAPI.
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
- [[security/terminal-authentication]] — identidade operacional individual e contrato do Terminal Python.
- [[security/terminal-device-credentials]] — geração, hash, persistência e APIs administrativas.
- [[security/terminal-credential-rotation]] — revogação, rotação e transição do token global.
- [[security/websocket-security]] — handshake, Principal, heartbeat e origins.
- [[security/stomp-security]] — CONNECT, SUBSCRIBE, SEND e revalidação de sessão.
- [[security/terminal-cross-tenant]] — isolamento entre equipamentos e tenants.
- [[terminal-integration]] — contrato executável entre Terminal Python e backend.
- [[terminal-python]] — distribuição do cliente, layout imutável e fronteira com o backend.
- [[e2e-terminal-backend]] — execução real reproduzível com MySQL, Redis e Point stubado.
- [[security/http-authorization]] — allowlist HTTP, identidades, ownership e checkout.
- [[security/idor-protection]] — proteção cross-user, cross-terminal e cross-tenant.
- [[security/password-recovery-security]] — anti-enumeração, rate limit, HMAC, TTL e uso único.
- [[security/public-endpoints]] — inventário e justificativa de todas as exceções públicas.
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
- [[armazenamento-imagens]] — imagens no OCI Object Storage (API S3), limites e configuração.
- [[mercado-pago-account-lifecycle]] — exclusividade ativa, histórico, reutilização, unlink e substituição de conta/Point.
- [[payment-reconciliation]] — recuperação após backend/webhook offline, endpoint, scheduler, startup e idempotência.
- [[payment-point-flow]] — loading curto, tela Point, cancelamento integral e proteção contra segunda cobrança.
- [[payment-recovery]] — regra ponta a ponta contra cobrança duplicada, máquina de estados e roteiro operacional com Point offline.
- [[payment/payment-transaction-audit]] — auditoria do fluxo financeiro anterior ao P1-C.
- [[payment/order-price-finalization]] — pipeline autoritativa, snapshot e congelamento do preço.
- [[payment/payment-attempt-policy]] — múltiplas tentativas, estados, retry e idempotência.
- [[payment/coupon-payment-coordination]] — reserva, consumo e liberação transacional do cupom.
- [[payment/payment-concurrency]] — locks, constraint de tentativa ativa e tratamento de `UNKNOWN`.
- [[payment/terminal-payment-authorization]] — credencial interna e ownership Terminal/tenant.
- [[payment/point-refund]] — reembolso integral Point, recuperação, estoque e reversão de fidelidade.
- [[comprovante-compra]] — solicitação explícita e idempotente Spring Boot → FastAPI → n8n para comprovante PNG.

## Auditoria e evolução

- [[auditoria-bugs]] — bugs confirmados e riscos potenciais com evidências no código.
- [[melhorias]] — plano priorizado, dívida técnica, qualidade da API e cobertura de testes.

## Escopo desta documentação

O schema executável é definido por vinte e duas migrations (`V1` a `V22`). A V17
adiciona o convite seguro de vínculo conta/tenant e a V18 fecha o P1-B de configuração,
fidelidade, recompensas, cupons e constraints. A V19 fecha preço, cupom e tentativa
de pagamento, e a V20 adiciona credenciais individuais revogáveis/rotacionáveis de
Terminal. A cadeia histórica V1–V26 foi
removida do caminho ativo e exige recriação do banco de desenvolvimento. A V21
adiciona o vínculo seguro do cliente no carrinho e tokens de identificação com hash;
a V22 persiste intenção, idempotência e confirmação do reembolso Point.
