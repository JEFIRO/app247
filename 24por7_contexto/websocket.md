# WebSocket

Voltar para [[00-index]]. Fluxo de pagamento em [[mercado-pago]] e arquitetura geral em [[arquitetura]].

O projeto habilita simultaneamente WebSocket nativo e STOMP. Ambos aceitam qualquer origem e não implementam autenticação específica no handshake ou nas mensagens.

## WebSocket nativo do terminal

Endpoint: `/terminal-socket`.

`TerminalWebSocketHandler` recebe mensagens de texto e desserializa o payload como:

```json
{
  "terminalId": "uuid-do-terminal",
  "status": "ONLINE"
}
```

O `terminalId` é `String` no DTO; o handler não invoca Bean Validation explicitamente. O service busca o terminal globalmente pelo repository, converte `status` com `TerminalStatus.valueOf` e atualiza status, `update_at` e `lastPing`.

Um job executado a cada 60 segundos percorre todos os terminais e marca como `OFFLINE` os cujo `lastPing` não nulo seja anterior a 60 segundos.

## WebSocket nativo de pagamento

Endpoint registrado: `/payment-socket/*`. O cliente deve conectar usando o identificador do terminal como último segmento, por exemplo `/payment-socket/{terminalId}`.

Ao conectar, `PaymentWebSocketHandler` extrai esse segmento e mantém uma única `WebSocketSession` por `terminalId` em memória. Uma nova conexão para o mesmo ID substitui a anterior no mapa. Ao fechar, a sessão é removida.

O handler escuta `PaymentEvent` e, quando há sessão aberta para o terminal, envia JSON com:

- `type: PAYMENT_STATUS`;
- `terminalId`;
- `orderId`;
- `transactionId`;
- `status`: `WAITING_PAYMENT`, `APPROVED`, `REJECTED`, `CANCELLED`, `EXPIRED`, `ACTION_REQUIRED` ou `REFUNDED`;
- `mercadoPagoStatus`;
- `statusDetail`;
- `message`;
- `paid`, mantido temporariamente para compatibilidade com o contrato antigo.

O terminal é obtido exclusivamente por `Order -> Carrinho -> Terminal`; não há broadcast. Os listeners executam em `AFTER_COMMIT`. Falha de envio ou terminal offline é registrada, mas não reverte Pagamento, Order ou estoque.

Não há replay ou clusterização das sessões. Para recuperação, o Terminal consulta `GET /order/{orderId}/status?terminalId={terminalId}`, que lê o estado persistido sem criar nova cobrança.

## STOMP

Handshake STOMP: `/ws`.

Configuração do broker:

- broker simples em memória para destinos `/topic`;
- prefixo de entrada da aplicação `/app`;
- publicação de pagamento em `/topic/payment/{terminalId}`.

`PaymentSocketService` escuta `PaymentEvent` de forma assíncrona e publica o próprio evento nesse tópico. Não há método `@MessageMapping`, portanto o projeto não define mensagens de entrada STOMP sob `/app`.

## Relação com o fluxo de pagamento

Os dois mecanismos de saída — WebSocket nativo e STOMP — escutam o mesmo `PaymentEvent`. `PagamentoService` publica o evento quando uma transição Point é efetivamente aplicada. Aprovação, recusa, cancelamento, expiração, ação requerida, reembolso e estados intermediários são comunicados pelo mapper centralizado; evento duplicado/obsoleto não gera nova mensagem.

## Segurança e operação

- Origens são liberadas com `*`/padrão curinga.
- Não há associação entre a identidade autenticada e o `terminalId` informado na URL ou mensagem.
- As sessões ficam apenas na memória da instância; múltiplas réplicas não compartilham conexões.
- O broker STOMP é o simple broker interno, sem Redis/RabbitMQ como relay.
- Erros de desserialização ou status inválido sobem do handler; não há resposta de erro de domínio definida.
