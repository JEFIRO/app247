# Auditoria técnica — resumo executivo

Este resumo consolida a auditoria e as implementações realizadas até 16 de agosto de 2026. Os detalhes e as evidências estão em [[auditoria-bugs]], [[melhorias]], [[api]], [[arquitetura-geral]] e [[mapa-projeto]].

> As rotas HTTP permanecem temporariamente desprotegidas durante a fase de desenvolvimento e testes. Essa é uma decisão temporária e não representa a configuração desejada para produção.

Nenhuma exigência de autenticação, role ou `@PreAuthorize` foi mantida na camada HTTP. `SecurityConfig` usa uma única regra `anyRequest().permitAll()`; o filtro apenas preenche o contexto quando o cliente envia um JWT válido.

## Estado geral

| Área | Estado observado |
|---|---|
| Arquitetura | Backend Spring Boot organizado em domínio e infraestrutura, mas com services e contratos ainda acoplados a entidades JPA e com alguns fluxos distribuídos entre controller, service, eventos e workers. |
| Segurança | JWT e papéis existem, mas a regra residual aberta foi mantida por decisão temporária. Segredos literais foram externalizados; valores previamente expostos ainda exigem rotação. |
| Multi-tenancy | A hierarquia `Empresa -> Condomínio -> Terminal` permanece. Carrinho valida o contexto quando presente e sempre deriva empresa do terminal; Order de teste não pode misturar usuário/carrinho entre empresas. |
| Pagamentos | Credencial e maquininha permanecem separadas. Criação Point responde `WAITING_PAYMENT`, reutiliza cobrança ativa, webhook usa versão/lock/máquina de estados e publica resultado ao Terminal após commit. |
| Estoque | Há estoque por condomínio, movimentação auditável, reserva, venda/liberação idempotente e atualização pessimista. A correção depende da ordem das transições recebidas. |
| Qualidade da API | Há rotas REST recentes e coerentes ao lado de rotas legadas com verbos no path, mutações por `GET`, respostas de entidade e endpoints de teste públicos. Consulte [[api]]. |
| Banco de dados | V21–V23 alinham os novos campos/snapshots e produção usa `ddl-auto=validate`; o histórico antigo ainda precisa ser confrontado com os bancos implantados. |
| Redis e jobs | Filas usam estágio `processing`, três tentativas e DLQ. Recuperação automatizada de itens presos após crash continua pendente. |
| WebSocket | STOMP e WebSocket nativo recebem `PaymentEvent` direcionado ao terminal da Order; continuam sem autenticação efetiva e com origens amplamente permitidas. |
| Testes | A suíte passou com 68 testes. Usa H2 e Flyway desabilitado, portanto não valida migrations MySQL nem integrações externas reais. |

## Implementação concluída

- versão/data e transições monotônicas de webhook, com lock pessimista;
- finalização coerente de Pagamento, Order e Carrinho;
- DTOs explícitos para User, Order e Produto;
- segredos externalizados e `ddl-auto=validate` em produção;
- timeouts HTTP e erro externo sanitizado;
- filas de pagamento/e-mail com processing, retry e DLQ;
- usuário inativo, recuperação de senha e validação de e-mail corrigidos;
- helpers de Carrinho e snapshot de unidade de medida em Item;
- conteúdo de imagem validado e URL relativa;
- simuladores configuráveis e desabilitados no profile `prod`;
- migrations V21, V22 e V23.
- contrato Point para Terminal Python com `WAITING_PAYMENT`, notificações pós-commit e consulta após reconexão;
- expiração Point alinhada a 15 minutos e clique repetido serializado/reutilizado.
- integridade do carrinho revalidada antes da cobrança e falhas Point classificadas sem exposição do corpo externo;
- últimos segredos OAuth/SMTP literais removidos do arquivo base e substituídos por variáveis de ambiente.

## Pendência obrigatória de pré-produção

- [ ] Ativar autenticação e autorização HTTP
- [ ] Definir rotas públicas
- [ ] Proteger rotas administrativas
- [ ] Aplicar roles ADMIN/GERENTE
- [ ] Revisar CORS
- [ ] Desabilitar endpoints de teste/debug
- [ ] Rotacionar credenciais anteriormente versionadas
- [ ] Validar V1–V23 contra cópia MySQL e históricos Flyway reais

## Top 10 problemas

1. **BUG-001 — ADIADO:** dados e alterações de usuário continuam acessíveis sem autenticação.
2. **BUG-002 — ADIADO:** pedido, reserva e cobrança ainda podem ser iniciados anonimamente.
3. **BUG-004 — ABERTO:** histórico Flyway implantado ainda precisa ser reconciliado.
4. **BUG-011 — ADIADO:** WebSockets ainda permitem identidade declarada pelo cliente.
5. **BUG-003 — PARCIAL:** arquivos foram saneados, mas segredos anteriormente expostos precisam de rotação.
6. **BUG-005 — PARCIAL:** V21–V23 alinham o schema novo; falta validação MySQL/históricos reais.
7. **BUG-012 — PARCIAL:** processing/retry/DLQ existem; falta recuperação automatizada após crash.
8. **BUG-016 — PARCIAL:** sessão não pode trocar usuário, mas o primeiro vínculo continua público.
9. **BUG-017 — PARCIAL:** upload valida conteúdo/URL, mas continua público e local.
10. **BUG-018 — PARCIAL:** expiração OAuth é detectada, mas não há refresh automático.

Os identificadores, severidades, classificação como bug confirmado ou risco potencial e locais exatos estão em [[auditoria-bugs]].

## Top 10 melhorias

1. **P0 — MEL-001 (ADIADA):** fechar a matriz de autorização e eliminar o `permitAll` residual antes de produção.
2. **P0 — MEL-002 (PARCIAL):** propriedades foram externalizadas; falta rotacionar os segredos expostos anteriormente.
3. **P0 — MEL-003 (PARCIAL):** V21–V23 alinham o modelo novo; falta reconciliar e validar o histórico Flyway em MySQL real.
4. **P0 — MEL-005 (IMPLEMENTADA):** máquina de estados monotônica, lock e versão externa do webhook.
5. **P0 — MEL-006 (IMPLEMENTADA):** rotas sandbox condicionais e desabilitadas no profile `prod`.
6. **P1 — MEL-004 (PARCIAL):** respostas públicas críticas migradas para DTOs; revisar endpoints legados restantes.
7. **P1 — MEL-008 (IMPLEMENTADA NO FLUXO ATUAL):** finalização e atualização de compra possuem fronteiras transacionais e estado coerente.
8. **P1 — MEL-009 (PARCIAL):** processing, retry limitado e DLQ implementados; falta recuperação automática de mensagens órfãs.
9. **P1 — MEL-010 (IMPLEMENTADA):** timeouts centralizados e falhas externas sanitizadas como `502`.
10. **P1 — MEL-011 (ADIADA):** autenticar terminais e autorizar canais WebSocket por empresa/condomínio antes de produção.

O plano completo, custo, benefício, arquivos afetados e padronização sugerida das rotas estão em [[melhorias]].

## Riscos antes de produção

- A exposição atual de rotas permite leitura ou mutação sem autenticação e quebra o isolamento tenant em caminhos legados.
- Segredos removidos dos arquivos atuais devem ser considerados comprometidos e rotacionados por terem existido no histórico.
- O histórico Flyway pode bloquear uma atualização por checksum ou produzir schema diferente em uma instalação nova.
- Webhooks repetidos e fora de ordem agora são contidos por versão, lock e máquina de estados; é necessário validar o fluxo contra eventos reais em homologação.
- O processamento Redis preserva a mensagem em `processing`, mas ainda requer procedimento de recuperação após queda.
- WebSocket sem autenticação permite observação ou emissão de eventos com identificadores conhecidos.
- A suíte não prova compatibilidade com MySQL/Flyway, Redis real, SMTP ou Mercado Pago.
- Tokens OAuth expiram e não há fluxo automático de renovação implementado.
- Contratos migrados para DTO devem ser validados nos clientes Flutter/terminal.
- Endpoints de teste são configuráveis e ficam desabilitados no profile `prod`, mas continuam públicos quando habilitados.

## O que está bem estruturado

- A fonte de verdade de terminais segue `Empresa -> Condomínio -> Terminal`, sem associação direta redundante entre terminal e empresa.
- `MercadoPagoConta` é única por empresa e armazena credenciais; `Terminal.mercadoPagoTerminalId` armazena a identificação física da maquininha.
- A criação Point obtém token e maquininha por cadeias distintas e coerentes com o tenant.
- O estoque pertence ao par único condomínio/produto e aceita quantidade negativa com registro e warning.
- Movimentações possuem chave idempotente única e o estoque usa lock pessimista, reduzindo duplicidade e lost update.
- O onboarding possui caso de uso próprio e fronteira transacional.
- Há validação HMAC do webhook e busca do recurso atualizado no Mercado Pago antes do processamento.
- Repositories recentes oferecem consultas que incluem empresa na própria condição de busca.
- Os testes cobrem hierarquia tenant, unicidade de conta/maquininha, estoque, cobrança Point, OAuth e assinatura de webhook.
- O fluxo Point possui testes para resposta inicial, estados definitivos/intermediários, duplicidade, ordem de eventos, Terminal correto e recuperação após desconexão.
- A documentação arquitetural já registra decisões explícitas em [[decisoes/001-hierarquia-empresa-condominio-terminal]] e [[decisoes/002-credenciais-e-terminais-mercado-pago]].

## Verificação executada

Em 16 de agosto de 2026:

| Verificação | Resultado |
|---|---|
| `mvn test` | **PASSOU** — 182 fontes compiladas; 68 testes, 0 falhas, 0 erros e 0 ignorados. |
| `git diff --check` | **PASSOU** durante a validação; será repetido após o fechamento documental. |

Os testes executaram com Java 17 e perfil `test`. Os testes JPA usam H2 e o arquivo `src/test/resources/application-test.properties` desabilita Flyway. Assim, o resultado confirma compilação e comportamento coberto pela suíte, mas **não** valida a aplicação sequencial de `V1` a `V23` em MySQL nem a comunicação real com Redis, SMTP e Mercado Pago. Não houve falha de código ou de ambiente durante os comandos acima.

## Próximo passo sugerido

Executar as ações externas e pré-produção acima. Depois, concluir recuperação segura das filas `processing`, refresh OAuth conforme documentação oficial validada e autenticação dos terminais/WebSockets. Expandir a suíte com MySQL efêmero/Flyway e testes HTTP de autorização conforme [[melhorias]].
