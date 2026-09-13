# Auditoria da interface Flutter

Atualizada em 2026-09-05 após leitura integral da estrutura oficial e comparação com o backend.

## Arquitetura encontrada e preservada

O app usa `StatefulWidget`/`setState`, services HTTP por domínio e models/DTOs manuais. Não havia Provider, Riverpod, BLoC ou GetX; nenhum novo gerenciador foi introduzido. A evolução é aditiva:

```text
lib/
├── main.dart
├── core/
│   ├── navigation/   rotas Web nomeadas e guard de sessão
│   ├── platform/     arquivo, download e URL externa multiplataforma
│   ├── formatters/   moeda e datas America/Sao_Paulo
│   ├── theme/        tokens, ColorScheme, tipografia e branding
│   └── widgets/      shell, navegação e estados compartilhados
└── src/
    ├── model/        DTOs, valores decimais e repositories HTTP
    ├── page/         telas existentes e módulos administrativos
    ├── service/      sessão, onboarding, Mercado Pago e branding
    └── widget/       componentes legados preservados
```

O `main.dart` agora inicializa locale, timezone, sessão, cache de branding, estratégia de URL Web e roteamento. HTTP e parsing não foram colocados em widgets novos.

## Fluxo atual

```text
Welcome/Login
  → Dashboard operacional
     ├── Operação: Condomínios → Terminais → Telemetria/Alertas
     ├── Catálogo: Produtos → Produto e Promoções
     ├── Estoque: Central/Condomínios/Movimentos/Transferências/Inventários/Importação/Planograma
     ├── Vendas: Histórico e Pagamentos
     ├── Integrações: Mercado Pago/Point
     └── Configurações: Aparência
```

No mobile há cinco destinos: Início, Operação, Produtos, Estoque e Mais. Em desktop há sidebar agrupada; em tablet há rail compacto e rolável. O conteúdo usa limites de largura e breakpoints únicos.

## Telas reformuladas

- Home: KPIs de vendas, faturamento, Terminais, alertas e pagamentos; estoque baixo só aparece quando a regra backend estiver disponível; checklist e atividade são dados compostos do backend.
- Produto: seções Dados gerais, Preço, Códigos de barras, Estoque, Informações fiscais e Promoções; duas colunas em desktop e uma no mobile; submit único.
- Observabilidade: dashboard de Terminais, detalhe, métricas de CPU/RAM/temperatura/disco/Wi-Fi/energia/WebSocket, histórico e alertas.
- Aparência: preview e gravação dinâmica do nome, logos e cores.
- Estoque avançado, promoções, vendas e pagamentos: interfaces conectadas aos endpoints existentes, sem mock.

## Estados e acessibilidade

As telas assíncronas novas distinguem loading, conteúdo, vazio e erro/retry. Botões usam alvo mínimo de 48 px pelo tema; status têm texto/ícone, foco Material e contraste calculado para cores de branding. Tabelas têm rolagem horizontal e listas grandes usam paginação backend quando o contrato oferece `Page`.

## Código legado

Os fluxos oficiais anteriores de onboarding, Condomínios, Terminais e Mercado Pago foram mantidos. Alguns arquivos históricos com nomes fora do padrão e a antiga `HomePage` permanecem para compatibilidade de imports/testes, mas o roteador oficial entra em `AdminShellPage`/`DashboardPage`. A remoção deve ocorrer somente numa limpeza dedicada, após confirmar ausência de consumidores.
