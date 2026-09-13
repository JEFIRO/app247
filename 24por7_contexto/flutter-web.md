# Flutter Web

Atualizado em 2026-09-05. Web é alvo oficial junto com Android.

## Execução e build

```bash
flutter run -d chrome --dart-define=API_URL=http://localhost:8080
flutter build web --release --dart-define=API_URL=https://api.exemplo.com
```

`API_URL` passado por `--dart-define` tem prioridade sobre `.env`; isso permite
usar o mesmo código em desenvolvimento e publicação sem embutir segredo.

O `web/index.html` inicializa o bundle e `manifest.json` declara o painel. `usePathUrlStrategy()` produz URLs sem `#`. Arquivo, download e URL externa passam por `AppPlatformService`; não há `dart:html` espalhado e os services compartilhados não importam `dart:io`.

## Rotas diretas

O router reconhece dashboard, Produtos/detalhe/novo, Condomínios/detalhe, Terminais/detalhe/telemetria, estoque e seus submódulos, vendas, pagamentos, Mercado Pago, alertas e configurações. Usuário sem sessão é levado ao login e retorna ao destino solicitado.

## Requisito de hosting para F5

O servidor deve devolver `index.html` para caminhos que não sejam arquivos. Exemplos:

```nginx
location / {
  try_files $uri $uri/ /index.html;
}
```

No Firebase Hosting, usar rewrite de `**` para `/index.html`; em Apache, usar fallback equivalente. Sem rewrite, F5 em `/produtos/{id}` retorna 404 antes que o Flutter seja iniciado. A API libera por padrão apenas origens locais; cada ambiente publicado deve configurar `APP_CORS_ALLOWED_ORIGIN_PATTERNS` com a allowlist Web, separada por vírgulas.

## Responsividade validada por teste

Breakpoints centralizados cobrem 360, 600, 1024, 1366 e 1920 px. Mobile usa uma coluna/bottom navigation; tablet usa rail rolável; desktop usa sidebar e grids/duas colunas com largura máxima.
