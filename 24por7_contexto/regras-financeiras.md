# Regras financeiras

Voltar para [[00-index]].

`MoneyPolicy` centraliza banco/snapshots em `DECIMAL(15,6)`, Java em `BigDecimal`, escala de divisão 12 e persistência em 6 casas. Dinheiro nunca usa `double`, `float` ou `new BigDecimal(double)`.

O fluxo calcula o preço preciso por item, multiplica pela quantidade, soma subtotais precisos e somente então arredonda o total cobrado para 2 casas com `HALF_UP`. Exemplo: `10.165750 + 7.347625 + 5.127500 = 22.640875`; cobrança `22.64`, persistida como `22.640000`.

`Order.totalCalculado` conserva a soma precisa. `Order.totalCobrado` conserva a cobrança; `Order.total` permanece por compatibilidade e representa o total cobrado. Mercado Pago recebe somente esse valor de duas casas.

Nos clientes, Flutter conserva dinheiro como string decimal e o Terminal usa `Decimal`; o SQLite armazena preço como `TEXT` normalizado, nunca `REAL`.
