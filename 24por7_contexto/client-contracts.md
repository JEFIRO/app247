# Contratos Backend, Flutter e Terminal

Este documento registra os DTOs públicos após a reconstrução do banco. Entidades JPA não são contratos dos clientes.

## Produto e catálogo

| Semântica | Backend DTO | Flutter | Terminal SQLite |
|---|---|---|---|
| UUID do produto | `id` | `Produto.id` | `produtos.id` |
| SKU da empresa | `codigoInterno` | `Produto.codigoInterno` | `produtos.codigo_interno` |
| compatibilidade legada | `codigo` | somente fallback | barcode principal/fallback de payload antigo |
| códigos escaneáveis | `codigosBarras[]` | `ProdutoCodigoBarras[]` | `produto_codigo_barras` |
| preço normal/aplicado | `precoOriginal`/`preco` | `DecimalValue` | `TEXT` com seis casas |
| quantidade do condomínio | `quantidade` | `DecimalValue` no estoque | `TEXT` com três casas |
| promoção | `emPromocao`, IDs/nome | modelos de promoção | colunas de cache em `produtos` |
| peso/tolerância | decimal opcional | `DecimalValue?` | `TEXT` com três casas |

`GET /produtos/sync` mantém `codigo` por compatibilidade e também entrega `codigoInterno` e a lista completa de códigos ativos. Um UPSERT substitui atomicamente todos os códigos locais do produto; REMOVE desativa o produto e remove seus códigos escaneáveis.

Cadastro/edição administrativa usa `POST/PUT /produtos` com JSON ou multipart. O body separa `codigoInterno`, `codigosBarras[]`, dados comerciais e `fiscal` opcional. A disponibilidade é operação separada em `POST /condominios/{id}/estoque`.

## Condomínio e Terminal

- Flutter lista `GET /condominios` e `GET /terminais`, sempre sob o tenant do JWT.
- Terminal Python envia somente seu UUID técnico; empresa e condomínio são derivados pelo backend.
- Quantidade de estoque aceita três casas e saldo negativo. Disponibilidade depende de `ativo`.

## Carrinho e venda

O Terminal envia `CarrinhoRequest.items[]` com `productId`, `quantity`, `receivedWeight`, `expectedUnitPrice` e `codigoBarras`. O backend valida se o barcode pertence ao produto/tenant e cria seus próprios `CartItem` e snapshots `OrderItem`.

## Order e pagamento

| Semântica | Campo público |
|---|---|
| venda | `orderId` |
| tentativa local | `paymentAttemptId` (`paymentId` continua como alias no status) |
| transação remota | `transactionId` |
| estado seguro para UI | `status` |
| estado remoto diagnóstico | `mercadoPagoStatus` |

Eventos WebSocket e respostas de status incluem `orderId + paymentAttemptId`. O Terminal ignora evento de outra tentativa quando já conhece a tentativa corrente. WebSocket é otimização; `GET /order/{orderId}/status` continua sendo recuperação.

## Onboarding

`POST /onboarding` recebe `{gestor, empresa, condominio, terminal}` e devolve os quatro UUIDs. O Flutter não envia empresa em operações autenticadas posteriores; o tenant vem do JWT.

## Inventário e importação

Inventário usa `InventarioResponse`/`InventarioItem`, com quantidades de três casas, barcode principal para compatibilidade e `codigosBarras[]` para busca completa. Em modo cego, saldo/diferença são `null` durante a contagem; o Flutter não tenta reconstruí-los.

Importação usa `ImportacaoProdutoResponse` e preview por aba/linha. O Flutter transmite somente bytes XLSX e nome seguro em multipart; parsing, Empresa, validação e persistência ficam no backend. O Terminal Python não consome esses contratos administrativos e continua recebendo novos produtos somente pelo Product Sync após associação a um Condomínio.
