# Auditoria Backend × Flutter

Atualizada em 2026-09-05. Fontes auditadas: Flutter oficial em `/home/jefiro/StudioProjects/24por7`, backend oficial em `/home/jefiro/Documentos/projetos/app247`, documentação `24por7_contexto` e, apenas como referência, os documentos do diretório criado por engano.

Os estados abaixo descrevem integração real. `PARCIAL` significa que há contrato e interface utilizável, mas faltam operações detalhadas. Nenhuma linha marcada como ausente ganhou UI simulada.

| Funcionalidade | Backend oficial | Flutter oficial | Estado | Ação/observação |
|---|---|---|---|---|
| Login administrativo | `POST /auth/login/admin`, JWT e `EmpresaContext` | Login, persistência de sessão, guard de rotas e logout | IMPLEMENTADO | Não registra token; armazenamento seguro nativo ainda é evolução pendente. |
| Cadastro | `POST /onboarding` e alias legado | Onboarding completo existente preservado | IMPLEMENTADO | Contrato canônico sem `empresaId` arbitrário. |
| Onboarding | Transação Gestor → Empresa → Condomínio → Terminal | Formulário e checklist operacional | IMPLEMENTADO | Checklist deriva de `/admin/onboarding-status`. |
| Empresa | GET/PUT por ID com validação de tenant | Sem tela cadastral geral; nome aparece no dashboard | PARCIAL | Criar `/empresas/me` antes de expor gestão completa sem ID da UI. |
| Branding | GET/PUT `/empresas/me/branding`; V11 | Tema dinâmico, cache, fallback e tela Aparência | IMPLEMENTADO | URLs de logo HTTPS e cores `#RRGGBB`; não há upload de logo dedicado. |
| Condomínios | CRUD tenant-aware | Lista, detalhe, edição, estoque e Terminais | IMPLEMENTADO | Hierarquia oficial Empresa → Condomínio → Terminal preservada. |
| Terminais | CRUD, vínculo Point e monitoramento | Lista, detalhe, edição, Point e atalho para telemetria | IMPLEMENTADO | Lista administrativa mantém contexto do Condomínio. |
| Produto | CRUD lógico/paginado, disponibilidade | Catálogo paginado e editor responsivo | IMPLEMENTADO | Sem DELETE fictício. |
| ProdutoCodigoBarras | Múltiplos códigos, principal, ativo e unicidade por Empresa | Adicionar, remover, principal e ativo/inativo | IMPLEMENTADO | O Flutter envia a coleção real. |
| ProdutoFiscal | DTO/entidade opcionais | Seção fiscal opcional | IMPLEMENTADO | Nenhuma NFC-e foi criada. |
| Promoções | CRUD/status e cálculo backend | Lista, formulário e detalhe; atalho no Produto | IMPLEMENTADO | Não replica cálculo de preço no cliente. |
| EstoqueEmpresa | Listagem, entrada, saída e ajuste com ledger | Estoque central e movimentações | IMPLEMENTADO | Decimal textual preserva escala. |
| EstoqueCondominio | Associação, saldo, disponibilidade e movimentos | Estoque por mercado e associação no Produto | IMPLEMENTADO | Não duplica Produto. |
| MovimentacaoEstoque | Ledger imutável | Histórico operacional | IMPLEMENTADO | Sem edição direta de saldo. |
| TransferenciaEstoque | Criar/listar/detalhar/confirmar/cancelar | Fluxo de transferência | IMPLEMENTADO | Backend aplica movimentos correlacionados. |
| Inventário | Criar, contar, finalizar, cancelar e conflitos | Lista e detalhe/contagem | IMPLEMENTADO | Conflitos permanecem explícitos. |
| Importação | Modelo, validação, preview, confirmação e erros | Upload/download multiplataforma e acompanhamento | IMPLEMENTADO | Nenhuma importação é confirmada com erros. |
| Planograma | CRUD e posições/produtos | Gestão de planograma | IMPLEMENTADO | Módulo opcional e real. |
| Orders/Vendas | Agregação e lista `/admin/sales*` com snapshot | Histórico, filtros e paginação | PARCIAL | Falta endpoint administrativo de detalhe completo da venda. |
| PaymentAttempt | Resumo e lista administrativa | Histórico e filtros | PARCIAL | Lista real; falta detalhe administrativo dedicado. |
| PaymentEvent | Entidade/eventos no backend | Não há timeline detalhada acessível por endpoint admin | AUSENTE_NO_FLUTTER | Depende de endpoint tenant-aware de detalhe/timeline. |
| Pagamentos | `/admin/payments*`, reconciliação existente | Lista e indicador de atenção | PARCIAL | PENDING só conta após limite definido no backend. |
| Mercado Pago | OAuth, status e Points | Estados de configuração, OAuth e vínculo/desvínculo | IMPLEMENTADO | Tokens e secrets nunca chegam à UI. |
| OAuth | Callback e troca de código no backend | Abre autorização externa em Android/Web | IMPLEMENTADO | Callback público explícito; estado validado no backend. |
| Point | Listagem MP e vínculo no Terminal interno | Seleção e associação contextual | IMPLEMENTADO | Point pertence ao Terminal, não à conta OAuth. |
| Telemetria | Estado atual, histórico, dashboard e alertas | Central e detalhe com métricas/gráficos | IMPLEMENTADO | Limites e diagnóstico vêm do backend. |
| Heartbeat | Endpoint do Terminal e cálculo de status | Online/offline e tempo relativo | IMPLEMENTADO | Flutter não inventa timeout operacional. |
| Alertas | Resumo e lista paginada | Resumo, lista e detalhe contextual | IMPLEMENTADO | Severidade não depende apenas da cor. |
| AuditLog | Entidade e `/admin/activity` recente | Atividade recente na Home | PARCIAL | Falta endpoint paginado de auditoria com filtros e tela completa. |
| Usuários | Login, recuperação e perfil individual | Login/recuperação existentes | PARCIAL | Não há CRUD administrativo tenant-aware de gestores. |
| Permissões | Roles `ADMIN`/`GERENTE` na segurança | Módulos administrativos autenticados | PARCIAL | Não existe matriz configurável de permissões no backend. |

## Endpoints administrativos integrados

- `GET /admin/dashboard`
- `GET /admin/onboarding-status`
- `GET /admin/terminals/summary`
- `GET /admin/alerts/summary`
- `GET /admin/alerts` paginado
- `GET /admin/payments/attention-summary`
- `GET /admin/payments` paginado
- `GET /admin/sales/summary`
- `GET /admin/sales` paginado
- `GET /admin/activity`
- `GET/PUT /empresas/me/branding`

Todos derivam a Empresa do JWT/`EmpresaContext`; nenhuma consulta administrativa aceita `empresaId` como autorização normal.

## Gaps que permanecem reais

1. Detalhe administrativo de venda com todos os `OrderItem` snapshots.
2. Timeline tenant-aware de `PaymentAttempt` + `PaymentEvent`.
3. Auditoria paginada e filtrável, além da atividade recente.
4. Gestão administrativa de usuários/permissões.
5. Regra persistida de estoque mínimo; até existir, o KPI informa indisponibilidade e não usa `quantidade < 5`.
6. Endpoint `/empresas/me` para edição geral sem transportar ID pela interface.
