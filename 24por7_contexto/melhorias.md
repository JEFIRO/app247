# Melhorias e dívida técnica

Voltar para [[00-index]]. Bugs concretos em [[auditoria-bugs]].

Prioridades: `P0` bloqueia produção/segurança; `P1` alta; `P2` planejável; `P3` conveniência.

# Curto prazo

## MEL-001 — Fechar a matriz de autorização

**Status: ADIADO — risco aceito temporariamente em desenvolvimento/testes. Obrigatório antes de produção.**

- Problema: `anyRequest().permitAll()` deixa usuários, compra, upload e testes públicos.
- Benefício: reduz exposição cross-tenant e operações anônimas.
- Complexidade: `MÉDIA`; prioridade `P0`.
- Arquivos: `SecurityConfig`, controllers de User/Carrinho/Order/Pagamento/File/Testes, filtro/identidade de terminal.
- Ação: definir consumidores antes de alterar contratos, adicionar testes MockMvc por role e recurso.

## MEL-002 — Rotacionar e externalizar segredos

**Status: PARCIALMENTE IMPLEMENTADO.** Segredos foram externalizados; rotação dos valores já expostos depende da operação dos provedores.

- Problema: credenciais literais/fallback sensível no repositório.
- Benefício: elimina comprometimento por acesso ao código.
- Complexidade: `MÉDIA`; prioridade `P0`.
- Arquivos: `application*.properties`, configuração de deploy.

## MEL-003 — Recuperar a integridade do histórico Flyway

**Status: PARCIALMENTE IMPLEMENTADO.** V21–V23 são migrations novas e produção usa `ddl-auto=validate`; inspeção dos históricos implantados continua aberta.

- Problema: migrations antigas alteradas/removidas e schema divergente.
- Benefício: deploy determinístico.
- Complexidade: `ALTA`; prioridade `P0`.
- Arquivos: migrations, entidades e configuração JPA.
- Ação: comparar todos os `flyway_schema_history`, restaurar checksums originais, criar apenas migrations novas e migrar para `ddl-auto=validate`.

## MEL-004 — DTOs para todas as respostas públicas

**Status: IMPLEMENTADO no escopo de User, Order, Produto e Carrinho auditado.**

- Problema: entidades JPA expostas, ciclos, lazy loading e PII.
- Benefício: contrato estável e redução de N+1/recursão.
- Complexidade: `MÉDIA`; prioridade `P0`.
- Arquivos: `OrderController`, `UserController`, `ProdutoController`, DTOs e mappers.

## MEL-005 — Máquina de estados e versão do webhook

**Status: IMPLEMENTADO.** Lock pessimista, versão/data persistidas e transições monotônicas; migration V21 e testes regressivos.

- Problema: eventos fora de ordem podem regredir estado/estoque.
- Benefício: consistência financeira e de inventário.
- Complexidade: `ALTA`; prioridade `P0`.
- Arquivos: `Order`, migration nova, `PagamentoService`, `EstoqueService`, testes.

## MEL-006 — Retirar endpoints de teste do perfil produtivo

**Status: IMPLEMENTADO com configuração.** Habilitados por padrão no desenvolvimento e desabilitados no profile `prod` por `app.test-endpoints.enabled=false`.

- Problema: simuladores externos públicos no artefato.
- Benefício: reduz superfície operacional perigosa.
- Complexidade: `BAIXA`; prioridade `P0`.
- Arquivos: `TestarWebhookMPController`, `TestarWebhookMP`, configuração por profile.

## MEL-007 — Corrigir desativação e recuperação de usuário

**Status: IMPLEMENTADO.**

- Problema: `ativo` não controla `isEnabled`; exceções de recuperação são embrulhadas.
- Benefício: bloqueio efetivo e respostas previsíveis.
- Complexidade: `BAIXA`; prioridade `P1`.
- Arquivos: `User`, `UserService`, `RestExceptionHandler`, testes.

## MEL-008 — Finalização transacional de compra

**Status: IMPLEMENTADO.** Aprovação finaliza Pagamento, Order e Carrinho antes de publicar o evento síncrono de estoque.

- Problema: aprovação não atualiza `Order.paidAt` nem `Carrinho.PAID`.
- Benefício: estado local coerente.
- Complexidade: `MÉDIA`; prioridade `P1`.
- Arquivos: `PagamentoService`, `Order`, `Carrinho`, eventos e testes.

## MEL-009 — Filas confiáveis e DLQ

**Status: PARCIALMENTE IMPLEMENTADO.** Filas intermediárias, três tentativas e DLQ existem para pagamento e e-mail; recuperação de `processing` após crash/múltiplas réplicas permanece pendente.

- Problema: retry infinito e janela de perda após `rightPop`.
- Benefício: processamento observável e recuperável.
- Complexidade: `MÉDIA`; prioridade `P1`.
- Arquivos: `PaymentWorker`, `EmailWorker`, Redis/configuração.
- Não exige tecnologia nova: pode usar listas processing/DLQ, contador e operações Redis existentes.

## MEL-010 — Timeouts e erros externos tipados

**Status: IMPLEMENTADO.** Timeouts configuráveis e `ExternalServiceException` com resposta 502 sanitizada.

- Problema: `RestTemplate` sem timeout e exceções externas viram `500` genérico.
- Benefício: degradação controlada e diagnóstico melhor.
- Complexidade: `BAIXA`; prioridade `P1`.
- Arquivos: `Config`, services Mercado Pago, `RestExceptionHandler`.

# Médio prazo

## MEL-011 — Autenticação de terminal e WebSocket

**Status: ADIADO.** Depende do provisionamento/autenticação dos clientes e deve ser concluído antes de produção.

- Problema: terminal escolhe sua identidade por payload/path.
- Benefício: impede personificação e prepara escala horizontal.
- Complexidade: `ALTA`; prioridade `P1`.
- Arquivos: configs/handlers WebSocket, `TerminalService`, provisionamento.

## MEL-012 — Refresh OAuth por empresa

**Status: PARCIALMENTE IMPLEMENTADO.** Expiração é detectada e exige nova autorização; refresh automático continua pendente por ausência de contrato externo validado nesta tarefa.

- Problema: tokens expiram sem renovação.
- Benefício: continuidade das cobranças.
- Complexidade: `MÉDIA`; prioridade `P1`.
- Arquivos: `OauthMercadoPagoService`, `MercadoPagoConta`, client MP.

## MEL-013 — Separar clients externos dos casos de uso

**Status: PARCIALMENTE IMPLEMENTADO.** Configuração HTTP e erros foram centralizados; clients específicos continuam como evolução P2.

- Problema: services constroem URLs/headers e interpretam HTTP diretamente.
- Benefício: testes simples, timeouts uniformes e menor acoplamento.
- Complexidade: `MÉDIA`; prioridade `P2`.
- Arquivos: services Mercado Pago e SMTP.
- Sugestão proporcional: interfaces locais `MercadoPagoOrdersClient`, `MercadoPagoOAuthClient`, `MercadoPagoTerminalClient`; não requer framework novo.

## MEL-014 — Consolidar eventos e transições

**Status: PARCIALMENTE IMPLEMENTADO.** A máquina de estados está em `OrderStatus`, o mapeamento externo/Terminal em `MercadoPagoStatusMapper` e `PaymentEvent` participa do fluxo ativo após commit. A consolidação das classes de estoque permanece aberta.

- Problema remanescente: quatro classes de evento de estoque e um evento antigo de cancelamento.
- Benefício: fluxo explícito e menos duplicidade.
- Complexidade: `MÉDIA`; prioridade `P2`.
- Arquivos: `infra.event`, `PagamentoService`, `EstoqueOrderListener`, WebSocket.

## MEL-015 — Queries projetadas e paginação

- Problema: movimentações carregam relações lazy, job percorre todos os terminais e algumas listagens não paginam.
- Benefício: evita N+1 e degradação por volume.
- Complexidade: `MÉDIA`; prioridade `P2`.
- Arquivos: repositories/DTOs de estoque, terminal e usuários.

## MEL-016 — Unificar sessão e deep link

**Status: IMPLEMENTADO para o deep link e prevenção de substituição de usuário.** Mudança do verbo HTTP permanece por compatibilidade.

- Problema: `app247://` e `app24por7://` divergem; associação usa GET público.
- Benefício: contrato de cliente consistente.
- Complexidade: `BAIXA`; prioridade `P1`.
- Arquivos: `CheckoutSessionResponseDTO`, `CheckoutSessionService`, controller e clientes.

## MEL-017 — Observabilidade operacional

**Status: PARCIALMENTE IMPLEMENTADO.** `System.out/printStackTrace` foram removidos do código executável auditado e logs evitam credenciais/payloads; métricas e correlation ID permanecem.

- Problema: `System.out`, ausência de correlation ID/métricas e pouca visibilidade de filas.
- Benefício: diagnóstico de pagamentos e estoque.
- Complexidade: `MÉDIA`; prioridade `P1`.
- Arquivos: workers, webhooks, Mercado Pago, filtro HTTP, Actuator.
- Ação: logs estruturados com orderId/mpOrderId/requestId sem tokens; gauges de fila/DLQ; contadores de webhook, retries, status e estoque negativo; latência externa.

# Longo prazo

## MEL-018 — Organização por domínio/caso de uso

- Problema: packages técnicos concentram classes heterogêneas e `UserService` mantém muitas responsabilidades.
- Benefício: limites mais claros sem dividir prematuramente o monólito.
- Complexidade: `ALTA`; prioridade `P2`.
- Áreas: identidade, tenant, catálogo/estoque, checkout/pedido, Mercado Pago.

## MEL-019 — Estratégia de WebSocket para múltiplas réplicas

- Problema: sessões e broker estão em memória local.
- Benefício: entrega previsível em escala horizontal.
- Complexidade: `ALTA`; prioridade `P3` até existir múltipla réplica.
- Arquivos: handlers/config WebSocket e deploy.

## MEL-020 — Versionar a API

- Problema: contratos legados e novos compartilham namespace sem política de depreciação.
- Benefício: evolução sem quebra silenciosa.
- Complexidade: `ALTA`; prioridade `P2`.
- Arquivos: controllers, documentação e consumidores.

# Qualidade e padronização da API

Propostas; nenhuma rota foi alterada nesta auditoria.

| Rota atual | Rota sugerida | Motivo | Impacto |
|---|---|---|---|
| `/user/...` | `/usuarios/...` ou `/me/...` | plural e separação self/admin | alto; exige clientes/alias |
| `POST /user/foto?id=` | `PUT /me/foto` | principal autenticado e sem ID livre | médio |
| `GET /order/finalizar` | `POST /carrinhos/{id}/checkout` | operação mutável e recurso correto | alto |
| `GET /order?carrinho_id=` | `GET /orders/{orderId}` | parâmetro atual tem nome incorreto | médio |
| `GET /pagamento/terminal/{cart}` | `POST /orders/{id}/pagamentos/point` | criação não deve usar GET | alto |
| `/carrinho` | `/carrinhos` | plural consistente | médio |
| `GET /checkout?...` | `PUT /checkout/sessions/{id}/usuario` | efeito explícito | médio |
| `GET /checkout/carrinho` | `POST /checkout/sessions` | cria sessão, portanto não é GET | médio |
| `/mp/oauth/...` | remover após depreciação | alias redundante com `/mercado-pago` | baixo após migração |
| `POST /condominio` | remover após depreciação | alias de onboarding semanticamente incorreto | médio |
| `/produtos/save-list` | `POST /produtos/lote` | idioma/padrão | baixo |
| `/produtos/id?id=` | `GET /produtos/{produtoId}` | recurso por ID; separar busca por código | médio |
| `/terminal/serial/{serial}` | `/terminais/ativacao/{serial}` | plural e finalidade | médio |
| `/api/testes/mercadopago/**` | somente profile/test interno | não é API produtiva | baixo para produção |

Também é recomendável padronizar `Order` para `Pedido` ou manter inglês em todos os contratos, usar camelCase em parâmetros (`carrinhoId`) e retornar `201` em criações de produto/carrinho/estoque.

# Dívida técnica

- `UserService` combina login, cadastro, recuperação, senha, perfil, foto, condomínio e e-mail.
- `MercadoPagoService` contém PIX/Checkout Pro sem controller, método de evento com valores fixos e grande bloco comentado.
- `TestarWebhookMP` é código de teste externo em `src/main`.
- `AutenticacaoService` está no package `domain.model.dto.auth` apesar de ser service.
- `ProdutoListagemDTO` está em controller.
- Mistura de injeção por campo e construtor; o documento local exige `@Autowired`, mas várias classes usam construtor.
- Nomes inconsistentes: `sourcePaiment`, `create_at`, `id_order`, `getPagamento`, `addCarinho`, português/inglês misturados.
- `System.out.println` e `printStackTrace` permanecem em controllers, workers, sockets e services.
- Exceções genéricas perdem causa HTTP/de domínio.
- `RestTemplate` é compartilhado sem interceptors/timeouts.
- `ObjectMapper` é instanciado diretamente, reduzindo customização central do Spring.
- Imports e tipos não usados: `LoginRequestDTO`, `RequestCheckout`, `WebhookEvent` no fluxo, métodos globais legados de `ProdutoRepository`, imports em controllers.
- Duas implementações WebSocket coexistem sem uso claramente definido.
- `@Data`/`@ToString` em entidades com relações JPA aumenta risco de lazy loading/recursão.
- `ddl-auto=update` e Flyway disputam responsabilidade pelo schema.
- URLs externas, descrição `Venda PDV`, versão `0.0.1` e host de upload ainda possuem valores hardcoded. A duração Point passou a ser configurável e usa `PT15M` por padrão.

# Cobertura de testes recomendada

## P0 — Segurança e autenticação

- MockMvc para toda a matriz de [[api]]: anônimo, USER, GERENTE, ADMIN e tenant divergente.
- login de usuário inativo; token inválido/expirado; header malformado; ausência de empresa.
- recuperação sem enumeração, tentativas/expiração e preservação dos status HTTP.

## P0 — Multi-tenant

- usuário A consultando/alterando usuário, carrinho e Order B;
- produto/estoque/terminal/conta MP entre empresas;
- OAuth concorrente e mesma conta MP em mais de uma empresa.

## P0 — Estoque e concorrência

- teste de integração com duas transações/threads no mesmo saldo;
- sequência out-of-order `processed -> failed` e `failed -> processed`;
- rollback de reserva com múltiplos itens quando um não está disponível;
- idempotência validada pela constraint real, não só por mocks;
- cancelamento/reembolso antes e depois da venda.

## P1 — Carrinho e pedidos

- lista nula/vazia, item nulo, preço/quantidade e produto de tenant diferente;
- constraint uma Order por carrinho sob concorrência;
- serialização dos responses e imutabilidade após checkout.

## P1 — Pagamentos e Mercado Pago

- timeouts, 4xx/5xx, corpo vazio, status desconhecido e token expirado;
- criação atômica Order/Pagamento e idempotency key;
- refresh OAuth e concorrência por empresa.

## P1 — Webhook e workers

- HMAC inválido/ausente, body divergente, Redis indisponível;
- queda após pop, retry limitado, poison message e DLQ;
- duas instâncias concorrentes.

## P2 — WebSocket, arquivos e e-mail

- autenticação/ownership de terminal;
- conexões duplicadas e erro de envio;
- conteúdo de arquivo inválido, tamanho, URL e storage;
- workers SMTP e listener assíncrono.

## Situação atual

Os testes existentes cobrem bem mapeamentos Mercado Pago principais, hierarquia administrativa, algumas restrições tenant, vínculo de maquininhas, onboarding e idempotência unitária de estoque. Não executam migrations MySQL: o profile de teste usa H2, `ddl-auto=create-drop` e Flyway desativado.

## MEL-021 — Contrato Point resiliente para Terminal Python

- Status: `IMPLEMENTADA` em 16 de agosto de 2026.
- Prioridade: `P0`; complexidade: `MÉDIA`.
- Problema: início retornava booleano, webhook não notificava o Terminal e não existia recuperação mínima após perda do socket.
- Benefício: resposta explícita, status centralizados, entrega direcionada após commit, clique idempotente e reconexão sem nova cobrança.
- Arquivos: `PagamentoController`, `OrderController`, `PagamentoService`, `OrderService`, `MercadoPagoCobrancaService`, `MercadoPagoStatusMapper`, `PaymentEvent` e handlers WebSocket.
- Testes: criação/resposta inicial, mapper completo, cobrança ativa, terminal correto/diferente, desconexão e consulta de status.

## MEL-022 — Integridade pré-cobrança e erros Point classificados

- Status: `IMPLEMENTADA` em 16 de agosto de 2026.
- Prioridade: `P0`; complexidade: `BAIXA`.
- Problema: carrinho vazio/inconsistente podia alcançar o checkout e falhas HTTP do Point eram agrupadas apenas pelo status HTTP.
- Solução: validação redundante no DTO e service, recálculo do subtotal sob lock e classificação interna sanitizada para autenticação, terminal, cobrança ativa, idempotência, payload, timeout e indisponibilidade.
- Arquivos: `CarrinhoRequest`, `ItemRequest`, `CarrinhoService`, `OrderService`, `ExternalServiceException`, `ExternalFailureType` e `MercadoPagoCobrancaService`.
- Testes: carrinho vazio, subtotal divergente e `already_queued_order_for_terminal`.
