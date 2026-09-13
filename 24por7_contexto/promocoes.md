# Promoções

Voltar para [[00-index]].

## Fonte de verdade e disponibilidade

O backend é a fonte de verdade: Flutter configura, Terminal recebe e exibe, e o backend recalcula no checkout. Promoções nunca criam disponibilidade. O produto precisa estar ativo e possuir `EstoqueCondominio.ativo = true`; quantidade negativa não impede venda nem promoção.

## Modelo e regras

- `Promocao`: empresa obrigatória, condomínio condicional, abrangência, tipo, valor `DECIMAL(15,6)`, período UTC, habilitação administrativa e prioridade.
- `PromocaoProduto`: associação explícita e única por promoção/produto.
- `EMPRESA`: `condominio_id` nulo; aplica onde cada produto estiver disponível.
- `CONDOMINIO`: exige condomínio da empresa atual e produto disponível nele.
- tipos V1: `PERCENTUAL`, `DESCONTO_FIXO`, `PRECO_FIXO`.

O status é calculado: desabilitada → `DESATIVADA`; antes do início → `AGENDADA`; em `[inicio, fim)` → `ATIVA`; a partir do fim → `ENCERRADA`.

`PricingService` calcula cada oferta isoladamente e escolhe o menor preço. Não há acúmulo. Empate: condomínio, maior prioridade e UUID lexicograficamente menor. Oferta que não reduz o preço é ignorada.

## Venda e histórico

O item congela preço original, preço aplicado, promoção, tipo/valor, desconto e subtotal. `expectedUnitPrice` é somente a expectativa exibida. Se o recálculo aumentar algum preço, o backend devolve `409 PRICE_CHANGED` com os valores atuais e não cobra até nova confirmação.

Não existe exclusão no contrato V1. Desativação preserva o histórico e a FK opcional do item vendido.

## Terminal e tempo

Mutações publicam evento de catálogo entregue como `PRODUCT_SYNC_REQUIRED` somente `AFTER_COMMIT`. Promoção de condomínio notifica aquele condomínio; promoção da empresa notifica somente condomínios com os produtos disponíveis.

O scheduler detecta inícios/fins a cada minuto. O sync incremental também inclui transições em `(lastSync, syncAt]`, garantindo recuperação após Terminal ou backend offline.
