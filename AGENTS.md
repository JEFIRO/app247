# App 24/7

Este projeto é o backend do sistema App 24/7.

## Contexto do projeto

A documentação técnica e arquitetural está localizada em:

`24por7_contexto/`

Antes de implementar alterações relevantes, comece lendo:

- `24por7_contexto/00-index.md`
- `24por7_contexto/contexto.md`
- `24por7_contexto/arquitetura.md`

Depois leia somente os documentos relacionados à tarefa.

## Stack

Backend:

- Java 17
- Spring Boot
- Spring Security
- JWT
- JPA/Hibernate
- Flyway
- MySQL
- Redis
- WebSocket

## Estrutura

O código principal está em:

`src/main/java/com/jefiro/app247`

Principais áreas:

- `domain.model` — entidades e modelos de domínio
- `infra` — infraestrutura, integração, controllers, repositories e services

## Regras

- Não altere contratos de API sem verificar os consumidores.
- Não altere entidades JPA sem verificar impacto no banco e migrations.
- Preserve isolamento multi-tenant.
- Não coloque secrets, tokens ou credenciais no código.
- Evite adicionar dependências sem necessidade.
- Não faça commit automaticamente.

## Documentação

Quando uma alteração modificar:

- arquitetura
- banco de dados
- autenticação
- Mercado Pago
- WebSocket
- fluxo do terminal
- contratos entre aplicações

atualize também a documentação correspondente em `24por7_contexto/`.

## Fonte de verdade

Quando documentação e código divergirem:

1. analise o código atual;
2. confirme o comportamento;
3. considere o código executável como fonte principal;
4. atualize a documentação desatualizada.

## Antes de finalizar uma tarefa

Verifique:

1. compilação
2. testes
3. migrations
4. impacto nos contratos da API
5. documentação