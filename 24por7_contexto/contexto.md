# Contexto do App 24/7

Voltar para [[00-index]]. Detalhes estruturais em [[arquitetura]].

## O que o backend faz hoje

O App 24/7 é uma API Spring Boot para operação de compras associadas a empresas e condomínios. O código implementa onboarding transacional de gestor, empresa, primeiro condomínio e primeiro terminal; gestão posterior de condomínios e terminais; cadastro de usuários; catálogo de produtos; criação de carrinhos e pedidos; sessões de checkout por QR Code; cobrança em terminal Mercado Pago Point; recepção assíncrona de webhooks; autenticação JWT; envio de e-mails; armazenamento local de imagens; e comunicação WebSocket.

O fluxo comercial observado é:

1. Produtos são cadastrados no catálogo da empresa; a quantidade disponível fica em `EstoqueCondominio`.
2. Um terminal envia itens e cria um `Carrinho` com `CartItem` mutável; o backend valida Terminal, códigos e produtos na mesma empresa.
3. O carrinho origina no máximo uma `Order`; `OrderItem` recebe um snapshot histórico independente e a criação reserva o estoque do condomínio do terminal.
4. Para cobrança Point, o backend obtém a credencial em `Empresa -> MercadoPagoConta` e a maquininha em `Carrinho/Order -> Terminal -> mercadoPagoTerminalId`.
5. Webhooks têm assinatura HMAC validada, são deduplicados e enfileirados no Redis para um worker com fila intermediária, retry limitado e DLQ.
6. O status recebido é aplicado à `PaymentAttempt` exata com lock, versão persistida e máquina de estados; a aprovação finaliza tentativa/Order/Carrinho e listeners confirmam ou liberam a reserva de forma idempotente. Uma Order pode conservar várias tentativas encerradas.

Também existe um fluxo de sessão de checkout: uma sessão temporária é armazenada no Redis por 15 minutos e pode ser codificada como `app247://session/{sessionId}` em um QR Code. O código permite associar um `userId` à sessão, mas não altera seu status nem a converte automaticamente em pedido.

## Aplicações e consumidores observáveis

O código expõe API REST, Swagger UI, recursos de arquivo, WebSocket nativo e STOMP. Há indícios explícitos de dois consumidores:

- terminal físico/software: serial de ativação, heartbeat por WebSocket, carrinho com `terminalId`, cobrança Point e socket por terminal;
- aplicativo móvel: deep link de sessão `app247://session/...` e retornos do Checkout Pro `meuapp://success`, `meuapp://pending` e `meuapp://failure`.

Não há código dos clientes neste repositório, portanto seus comportamentos além desses contratos não são documentados.

## Stack efetivamente configurada

- Java 17 e Maven;
- Spring Boot 4.0.6;
- Spring MVC, Validation, JPA/Hibernate e Spring Security;
- JWT com `java-jwt` e assinatura HMAC256;
- MySQL em desenvolvimento integrado e produção; H2 fica restrito aos testes unitários rápidos;
- Flyway com migrations MySQL;
- Redis para sessões temporárias, códigos/tokens e filas;
- WebSocket nativo e STOMP com broker simples em memória;
- SDK Java do Mercado Pago e chamadas REST diretas;
- JavaMail para e-mails;
- ZXing para QR Codes;
- armazenamento de arquivos no diretório local `uploads/`.

## Hierarquia operacional e multi-tenant

`Empresa` é a fronteira do tenant:

```text
Empresa 1 -> N Condomínios 1 -> N Terminais
Empresa 1 -> N Users
```

O terminal não possui associação direta com empresa; seu tenant é obtido por `terminal.condominio.empresa`. Usuários `ADMIN` e `GERENTE` são gestores, mas continuam usando a entidade autenticável `User`.

Cada empresa possui no máximo uma `MercadoPagoConta`, exclusiva para credenciais OAuth. A identificação da maquininha física não pertence à conta: cada `Terminal` interno pode guardar seu próprio `mercadoPagoTerminalId`, único globalmente. Veja [[decisoes/002-credenciais-e-terminais-mercado-pago]].

O JWT carrega `userId` e `empresaId`. O filtro compara esses claims com uma projeção de segurança que consulta o usuário e o estado atual da Empresa sem inicializar a associação JPA `LAZY`; somente então coloca o ID escalar em `EmpresaContext`, sempre limpo ao fim da requisição. As APIs administrativas permanecem públicas temporariamente na camada HTTP; quando recebem contexto, seus services usam consultas compostas por recurso e empresa, impedindo operar um ID pertencente a outro tenant.

Produtos e estoque administrativos são filtrados pela empresa de `EmpresaContext`. Na criação do carrinho, a empresa é derivada do terminal e todos os produtos são validados contra ela. A área legada de usuário ainda possui operações que não seguem consultas tenant-aware em todos os caminhos.

## Capacidades presentes, mas não ligadas ao fluxo principal

- `MercadoPagoService` implementa criação de PIX e preferência de Checkout Pro, mas nenhum controller chama esses métodos atualmente.
- O webhook publica `PaymentEvent` após commit em `/topic/payment/{terminalId}` e no WebSocket nativo `/payment-socket/{terminalId}`. O terminal correto é derivado da Order e pode recuperar o estado persistido por HTTP após reconexão.
- `CompraCanceladaEvent` é consumido pelo listener de estoque para liberar/devolver a reserva uma única vez.
- `WebhookEvent` e seu repository existem, mas a deduplicação ativa usa chaves Redis com TTL de 24 horas.
- `QRCodeService` é um gerador genérico, enquanto o checkout usa geração própria em `CheckoutSessionService`.
- Existem endpoints de teste do Mercado Pago que fazem chamadas reais à API configurada. Permanecem habilitados por padrão em desenvolvimento, mas `application-prod.properties` define `app.test-endpoints.enabled=false`.

## Configuração operacional

O servidor usa a porta da variável `PORT`, com padrão `8080`. Desenvolvimento integrado e produção recebem MySQL e Redis por variáveis de ambiente e usam `ddl-auto=validate`; Flyway é a única fonte do schema. Não há perfil ativo definido no arquivo base.

JWT, banco, SMTP e Mercado Pago são configurados por variáveis de ambiente; não há mais credenciais produtivas literais nos arquivos atuais. Valores que já apareceram no histórico ainda precisam ser rotacionados externamente.
