# ADR 002 — Credenciais e terminais Mercado Pago

Voltar para [[00-index]]. Relacionada a [[mercado-pago]], [[banco-de-dados]], [[contexto]] e [[decisoes/001-hierarquia-empresa-condominio-terminal]].

## Status

Aceita e implementada.

## Contexto

`MercadoPagoConta` guardava simultaneamente credenciais OAuth da empresa e um `terminalId` Point. Isso limitava toda a empresa a uma maquininha selecionada e permitia ao frontend enviar um terminal externo arbitrário. A cobrança usava o token e o terminal lidos do mesmo registro, ignorando o terminal interno da venda.

## Decisão

```text
Empresa 1 -> 1 MercadoPagoConta
Empresa 1 -> N Condomínios -> N Terminais -> mercadoPagoTerminalId
```

- `MercadoPagoConta` guarda exclusivamente access token, refresh token, public key, usuário Mercado Pago, tipo, scope, live mode e datas.
- `mercado_pago_conta.empresa_id` é único.
- `Terminal.mercadoPagoTerminalId` identifica a maquininha física e é único quando não nulo.
- Não existe relação direta `Terminal -> Empresa`.
- O vínculo consulta `/terminals/v1/list` com a credencial da empresa e aceita apenas IDs devolvidos por essa conta.
- A cobrança obtém o access token pela empresa da order e a maquininha pelo terminal interno da order.
- O OAuth state registra gestor e empresa; o callback rejeita mudança de vínculo ou papel. Nova autorização atualiza a conta existente da empresa.

## Contratos

- `GET /mercado-pago/oauth` inicia OAuth para a empresa autenticada.
- `GET /mercado-pago/oauth/callback` é público e exige state válido.
- `GET /mercado-pago/terminais` lista maquininhas da conta e informa vínculos internos.
- `PUT /terminais/{terminalId}/mercado-pago` vincula uma maquininha validada.
- `DELETE /terminais/{terminalId}/mercado-pago` remove o vínculo.

As rotas antigas de início/listagem com `idUser` permanecem temporariamente como aliases protegidos e ignoram o ID recebido. A antiga seleção via `POST /mp/oauth/terminal/{idUser}` foi removida porque aceitava um DTO externo arbitrário.

## Consequências

- Uma empresa pode operar várias maquininhas em vários condomínios.
- Credenciais e identificação física têm ciclos de vida independentes.
- A mesma maquininha não pode ser vinculada a dois terminais internos.
- Contas duplicadas existentes impedem V19; devem ser saneadas sem apagar tokens automaticamente.
- O access token expira e a data é persistida, mas refresh automático continua não implementado.
