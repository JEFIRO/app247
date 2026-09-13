# Telemetria dos Terminais

Este módulo monitora exclusivamente o Raspberry Pi e a aplicação Terminal Python. Não coleta bateria, carregamento, Wi-Fi, 4G, temperatura ou hardware interno da Point Mercado Pago.

## Fluxo

```text
Raspberry / Terminal Python
  -> POST /terminal/telemetry (estado operacional, 60 s por padrão)
  -> terminal_telemetry_current (UPSERT por Terminal)
  -> terminal_telemetry_history (amostra detalhada)
  -> TelemetryHealthService (SAUDAVEL / ATENCAO / CRITICO)
  -> TelemetryAlertService (abre, mantém e resolve sem duplicar)
  -> Flutter /terminais/monitoramento
```

O heartbeat continua separado, pequeno e frequente. Cada ACK persistido renova `terminal:online:{uuid}` no Redis com TTL de 75 segundos. Se Redis estiver indisponível, `Terminal.lastPing` é o fallback. O limite padrão de OFFLINE é 60 segundos e pode ser configurado.

## Métricas

- sistema: CPU, temperatura, memória, disco, load average e uptimes;
- energia: flags atuais e históricas de subtensão, throttling, frequência limitada e soft temperature limit, além do hexadecimal bruto;
- rede: interface, SSID, IP, sinal, alcance e latência HTTP da própria API;
- aplicação: versão, uptime, socket operacional, sync iniciado/concluído/bem-sucedido/erro, compra e pagamento ativos;
- display: largura, altura e orientação.

Ausência de sensor/comando é `null`, nunca zero inventado. O payload não contém empresa, condomínio, cliente, produtos, credenciais, tokens, senha/PSK Wi-Fi nem logs/stacktraces. O backend resolve `Terminal -> Condomínio -> Empresa`.

## Classificação e thresholds

`TelemetryThresholds` concentra os limites configuráveis. Os padrões são temperatura 70/80 °C, disco 80/90%, CPU 90%, sinal Wi-Fi abaixo de 35%, latência 500/1500 ms, sync atrasado em 900 s e offline em 60 s. Subtensão ou throttling atuais são críticos; ocorrências históricas desde o boot são atenção. Backend inacessível precisa ocorrer em duas amostras consecutivas para ficar crítico.

OFFLINE é calculado pelo heartbeat e sobrepõe a classificação da última telemetria. O dashboard nunca percorre o histórico: lê Terminais, presença e a tabela de estado atual.

## Alertas e retenção

Há alertas `TERMINAL_OFFLINE`, `HIGH_TEMPERATURE`, `UNDERVOLTAGE`, `THROTTLING`, `HIGH_DISK_USAGE`, `WEAK_WIFI`, `HIGH_BACKEND_LATENCY`, `WEBSOCKET_DISCONNECTED` e `SYNC_DELAYED`. `active_key` única impede spam: a ocorrência ativa é atualizada; quando normaliza, recebe `RESOLVED`/`resolved_at` e permanece no histórico.

O scheduler de presença roda a cada 30 segundos. A retenção remove histórico bruto mais antigo que 30 dias diariamente, em operação SQL em lote. Ambos os valores são configuráveis em `application.properties`.

## Persistência

`V26__create_terminal_telemetry.sql` cria `terminal_telemetry_current`, `terminal_telemetry_history` e `terminal_telemetry_alert`, com FKs para `terminal`, índice do status atual, índice `(terminal_id, captured_at)`, índice de retenção por `received_at` e índices dos alertas. Nenhuma tabela armazena `empresa_id`: a cadeia organizacional existente permanece a fonte de verdade.

## Limites

- o endpoint do Terminal segue o mecanismo operacional existente, baseado no UUID provisionado; autenticação criptográfica própria do dispositivo continua pendente juntamente com a dos sockets existentes;
- não há agregação de longo prazo; os dados brutos expiram;
- scanner HID não possui sinal confiável de saúde, então `scannerAvailable` não é enviado;
- validação física de temperatura e `vcgencmd` ainda depende de executar no Raspberry real.
