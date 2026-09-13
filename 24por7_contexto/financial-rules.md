# Regras financeiras

- Banco: `DECIMAL(15,6)` para preços, descontos, subtotais e pagamentos.
- Java: `BigDecimal`; nunca `double`/`float` em cálculo financeiro.
- Persistência: escala 6, política central `MoneyPolicy`.
- Cálculo intermediário: não arredondar para duas casas.
- Cobrança: arredondar somente o total efetivamente cobrado, escala 2 e `HALF_UP`.
- Banco do total cobrado: ainda `DECIMAL(15,6)`, por exemplo `22.640000`.

Exemplo:

```text
10.990000 × (1 - 7.500000 / 100) = 10.165750
```

Os itens são somados com precisão interna. `orders.total_calculado` preserva a soma e `orders.total_cobrado` preserva o valor efetivamente enviado ao provedor. `payment_attempt.amount_requested` recebe o total cobrado; `amount_approved` registra o valor confirmado pelo provedor.
