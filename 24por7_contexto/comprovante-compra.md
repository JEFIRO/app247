# Comprovante de compra

Voltar para [[00-index]]. Contratos públicos em [[api]], venda/pagamento em [[mercado-pago]] e execução em [[arquitetura]].

## Responsabilidades

```text
Order e PaymentAttempt aprovados
        ↓ solicitação explícita do usuário
Spring Boot (regra, snapshot e idempotência)
        ↓ application/json
FastAPI /comprovante (Pillow e QR Code)
        ↓ multipart/form-data
n8n (WhatsApp ou e-mail)
```

O Spring não gera PNG, não chama WhatsApp/e-mail e não conhece o webhook do n8n. A FastAPI não decide se a venda está aprovada: recebe apenas o snapshot já validado. Falha de FastAPI, n8n, WhatsApp ou e-mail nunca altera a venda, o pagamento ou o estoque.

O envio permanece opcional. A transição para `PROCESSED` somente habilita `POST /comprovante`; webhook, scheduler e reconciliação não disparam comprovante automaticamente.

## Snapshot da venda

`ComprovanteSnapshotService.montar` carrega a Order validando o Terminal e constrói o DTO em transação somente leitura. Isso inicializa `Empresa`, `Condominio/Endereco`, `Terminal`, `PaymentAttempt` e `Order.items` dentro da sessão JPA, sem habilitar Open Session in View nem tornar relações EAGER. A chamada HTTP acontece depois que esse método retorna, fora da transação.

Campos enviados:

| Campo | Fonte |
|---|---|
| `empresa`, `cnpj` | `Order.empresa.nomeFantasia/cnpj` |
| `endereco`, `local` | `Order.condominio.endereco` e nome do condomínio |
| `pedido` | `Order.idOrder`, sem prefixo artificial |
| `data`, `hora` | `PaymentAttempt.paidAt` em `America/Bahia`, `dd/MM/yyyy` e `HH:mm` |
| `terminal` | código comercial de `Order.terminal` |
| `itens[].nome` | snapshot `OrderItem.nome` |
| `itens[].quantidade` | snapshot `OrderItem.quantidade`, inteiro exato no contrato atual |
| `itens[].valor` | subtotal histórico `OrderItem.subtotalCalculado` |
| `total` | `Order.totalCobrado` |
| `pagamento` | meio confirmado da tentativa aprovada |
| `status` | `APROVADO` após as validações |
| `qr_data` | vazio enquanto não existir URL pública real |

Nenhum preço é consultado em `Produto`. Valores continuam `BigDecimal` até a apresentação e são formatados com locale `pt-BR` (`R$ 1.234,56`). O campo `valor` significa total da linha porque o gerador Python imprime esse valor ao lado de `quantidade + nome`.

## Contrato Spring → FastAPI

URL base: `app.comprovante.base-url`, configurada por `COMPROVANTE_SERVICE_URL`. O endpoint padrão é `/comprovante`. Em desenvolvimento o fallback é `http://localhost:8000`; no Compose é `http://comprovante-service:8000`.

```json
{
  "request_id": "sha256-estavel-gerado-pelo-spring",
  "canal": "WHATSAPP",
  "request": {
    "empresa": "Mercado Autônomo",
    "cnpj": "12.345.678/0001-90",
    "endereco": "Rua das Flores, 123 - Centro",
    "local": "Condomínio X - Feira de Santana/BA",
    "pedido": "uuid-order",
    "data": "12/09/2026",
    "terminal": "TERM-001",
    "hora": "15:40",
    "itens": [
      {"nome": "Coca-Cola 350ml", "quantidade": 2, "valor": "R$ 11,98"}
    ],
    "total": "R$ 11,98",
    "pagamento": "PIX",
    "status": "APROVADO",
    "qr_data": ""
  },
  "destinatario": "5575999999999"
}
```

A resposta esperada é `{"success":true,"pedido":"...","canal":"WHATSAPP","request_id":"...","status":"ACCEPTED"}`. Spring valida sucesso, pedido, canal, chave correlacionada e o estado `ACCEPTED`; HTTP 200 com corpo inconsistente não é sucesso. `ACCEPTED` significa somente que o n8n aceitou a solicitação, não que WhatsApp ou e-mail foi entregue.

O Spring atualizado sempre envia `canal` e `request_id`. Durante uma janela de migração, a FastAPI ainda aceita a ausência desses campos: infere o canal com warning e gera UUID temporário. Essa compatibilidade deve ser removida somente depois de todos os consumidores estarem atualizados.

O cliente dedicado usa connect timeout de 3 segundos e read timeout de 35 segundos, configuráveis por `COMPROVANTE_CONNECT_TIMEOUT` e `COMPROVANTE_READ_TIMEOUT`.

## Idempotência e falhas

A chave Redis é um SHA-256 de pedido, canal e destinatário normalizado; dados pessoais não fazem parte da chave legível. Ela é usada também como `request_id` e `X-Correlation-Id`. `PROCESSING` dura por padrão dois minutos e um sucesso aceito por um dia. Repetições concorrentes recebem `COMPROVANTE_ALREADY_PROCESSING`; repetições já confirmadas respondem `JA_ENVIADO` sem novo POST. A mesma chave é enviada como `X-Idempotency-Key` e a FastAPI a repassa ao n8n.

Connection refused libera o lease porque a FastAPI não recebeu a chamada. Read timeout e falhas 5xx mantêm o lease temporário: o estado do envio pode ser desconhecido e a repetição imediata arriscaria duplicidade. Redis indisponível impede iniciar o envio, pois não há garantia de deduplicação. Nenhum desses casos escreve em Order ou PaymentAttempt.

## FastAPI → n8n

O subserviço está em `comprovante-service/`. `POST /comprovante` gera o PNG em memória e envia multipart com:

- `destinatario`: telefone ou e-mail normalizado;
- `pedido`: ID da Order;
- `canal`: `WHATSAPP` ou `EMAIL`;
- `request_id`: chave recebida do Spring;
- `imagem`: `comprovante-<pedido-seguro>.png`, `image/png`.

Os headers enviados são `X-Correlation-Id` e `X-Idempotency-Key`. O workflow n8n não foi alterado nem inspecionado nesta tarefa: a deduplicação ponta a ponta permanece pendente até que ele persista e respeite a chave antes do envio.

O webhook existe somente na variável `N8N_COMPROVANTE_WEBHOOK_URL` da FastAPI. `GET /health` não gera imagem nem chama n8n; `GET /ready` valida somente configuração local e client HTTP. QR Code é omitido quando `qr_data` está vazio e nenhuma URL fictícia é inventada. O layout preserva “Este comprovante não é documento fiscal”.

## Docker e operação

O Compose usa a imagem configurada do `comprovante-service`, expõe apenas a porta interna 8000 e mantém a API principal independente do health check desse serviço opcional. Configure no ambiente de produção:

```text
N8N_COMPROVANTE_WEBHOOK_URL=https://.../webhook/comprovante
N8N_COMPROVANTE_TIMEOUT_SECONDS=30
COMPROVANTE_LOGO_PATH=/app/logo.png
COMPROVANTE_INTERNAL_TOKEN=um-secret-compartilhado
COMPROVANTE_SERVICE_URL=http://comprovante-service:8000
```

Quando `COMPROVANTE_INTERNAL_TOKEN` está preenchido nos dois processos, o Spring envia `X-Internal-Token` e a FastAPI compara o valor sem registrá-lo. Vazio preserva compatibilidade durante a implantação, mas não autentica o tráfego interno.

Para desenvolvimento fora do Docker:

```bash
cd comprovante-service
N8N_COMPROVANTE_WEBHOOK_URL=https://... uvicorn app:app --host 0.0.0.0 --port 8000
```

O teste ponta a ponta externo requer uma Order aprovada real, destinatário controlado e acesso ao webhook n8n. Testes automatizados usam FastAPI/RestTemplate mockados e não enviam mensagens reais.
