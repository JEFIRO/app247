# White-label dinâmico

Atualizado em 2026-09-05.

## Contrato

`GET /empresas/me/branding` e `PUT /empresas/me/branding` operam exclusivamente sobre `EmpresaContext.require()`. Campos:

```json
{
  "nomeExibicao": "Mercado Parque",
  "logoUrl": "https://cdn.exemplo/logo.png",
  "logoDarkUrl": "https://cdn.exemplo/logo-dark.png",
  "corPrincipal": "#169DFF",
  "corSecundaria": "#62C8FF",
  "corDestaque": "#00D084"
}
```

As URLs são opcionais e, quando informadas, devem ser HTTPS. Cores inválidas são recusadas pelo backend. Nenhuma credencial participa desse contrato.

## Ciclo Flutter

1. No startup, restaura sessão e branding em cache.
2. Renderiza imediatamente cache ou `BrandingConfig.fallback`.
3. Atualiza em background após autenticação.
4. Após login, busca o branding da Empresa.
5. Ao salvar Aparência, aplica tema e cache em uma única operação.
6. Em falha de rede/dado, mantém o último valor válido; branding nunca bloqueia a operação.
7. No logout, limpa sessão e cache para não exibir a marca de outra Empresa.

O backend adiciona os campos por `V11__company_branding.sql`. O dashboard usa `nomeExibicao`, com fallback para nome fantasia/razão social.

## Limites intencionais

O app aceita URLs de logo; upload/armazenamento de imagem institucional não foi inventado. Flavors, package name, ícone, splash, nome de loja e domínio próprio são possibilidade futura e não fazem parte desta entrega.
