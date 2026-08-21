# App 24/7 — documentação técnica

Esta base descreve o backend conforme o código presente no repositório. O código executável e as migrations são a fonte primária; limitações e divergências encontradas são registradas nos documentos, sem pressupor funcionalidades ainda não implementadas.

## Visão geral

- [[contexto]] — objetivo observado, capacidades, clientes e limites do sistema.
- [[arquitetura]] — packages, camadas, componentes e fluxos principais.
- [[arquitetura-geral]] — visão transversal da arquitetura, integrações e fluxos executáveis.
- [[mapa-projeto]] — mapa dos packages e responsabilidades reais das classes.
- [[api]] — catálogo completo das rotas e matriz da segurança atualmente configurada.
- [[auditoria-resumo]] — estado executivo, principais riscos e resultado das verificações.

## Infraestrutura e dados

- [[banco-de-dados]] — entidades JPA, relacionamentos, repositories, Flyway e divergências de schema.
- [[estoque]] — catálogo, estoque por condomínio, movimentações, concorrência e ciclo da venda.
- [[autenticacao]] — Spring Security, JWT, papéis, recuperação de senha e contexto de empresa.
- [[websocket]] — endpoints WebSocket nativos, STOMP e eventos de pagamento.

## Decisões arquiteturais

- [[decisoes/001-hierarquia-empresa-condominio-terminal]] — hierarquia operacional, gestores e onboarding.
- [[decisoes/002-credenciais-e-terminais-mercado-pago]] — separação entre OAuth da empresa e maquininha física.
- Sempre use @Autowired para injeção de dependências
## Integrações

- [[mercado-pago]] — OAuth, Point Orders, webhook, filas Redis e fluxos de pagamento.

## Auditoria e evolução

- [[auditoria-bugs]] — bugs confirmados e riscos potenciais com evidências no código.
- [[melhorias]] — plano priorizado, dívida técnica, qualidade da API e cobertura de testes.

## Escopo desta documentação

Foram analisados `src/main/java/com/jefiro/app247`, `src/test`, `src/main/resources`, `pom.xml` e as migrations `V1` a `V23`.
