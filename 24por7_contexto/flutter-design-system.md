# Design System Flutter

Atualizado em 2026-09-05.

## Estrutura

`lib/core/theme` concentra `app_theme.dart`, `app_color_scheme.dart`, `app_typography.dart`, `app_tokens.dart`, aliases de spacing/radius/breakpoints e `branding_config.dart`. `lib/core/widgets` fornece scaffold, navegação, top bar, cards, botões, campos, dropdown, busca, seções, badge, estados assíncronos, confirmação, tabela, paginação e feedback.

## Tokens

| Grupo | Valores |
|---|---|
| Espaçamento | 4, 8, 12, 16, 24, 32 e 40 para seção |
| Radius | 8, 12, 18 e pill |
| Breakpoints | mobile `< 600`; tablet `600–1023`; desktop `>= 1024` |
| Conteúdo | máximo 1440 px |
| Formulário | máximo 760 px por coluna/bloco |
| Sidebar / rail | 260 / 88 px |

O fallback mantém a identidade App 24/7 (`#169DFF`, `#62C8FF`, `#00D084`) sobre superfícies escuras. O `ColorScheme` é produzido pelo branding e calcula `onPrimary`, `onSecondary` e `onTertiary` pela luminância; `onSurface` e `onError` são explícitos.

## Regras de uso

- Preferir `Theme.of(context).colorScheme` e tokens a novos hexadecimais.
- Usar `AppContent` para padding e largura máxima.
- Usar `AppLoadingState`, `AppEmptyState` e `AppErrorState` para operações remotas.
- Usar `AppStatusBadge` com texto; cor sozinha não comunica estado.
- Usar `AppButton` ou botões temáticos com loading/desabilitação contra duplo submit.
- Valores financeiros permanecem como `DecimalValue`; arredondamento é apenas de apresentação.

As telas legadas ainda usam `src/theme/app_theme.dart`; a migração é incremental para não quebrar fluxos já testados.
