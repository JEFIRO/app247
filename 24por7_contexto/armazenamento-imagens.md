# Armazenamento de imagens (OCI Object Storage)

Desde 19/10/2026 as imagens ficam no **OCI Object Storage**, acessado pela API compatível com S3
(`software.amazon.awssdk:s3` **2.29.52**, fixado antes da 2.30, que passou a enviar checksums CRC por
padrão e não é aceita pelo endpoint S3 da Oracle). O armazenamento em disco (`uploads/`,
`FileStorageService`, `FileController`, `WebConfig` servindo `/files/**`) foi removido.

## Fluxo

`ObjectStorageService.uploadImage(file, pasta)`:

1. limite de tamanho `app.storage.image.max-size` (padrão 5 MB) → `413 IMAGE_TOO_LARGE`;
2. formato detectado pelo **conteúdo** (não pelo nome): só JPEG e PNG → `400 IMAGE_INVALID`;
3. dimensões lidas só do cabeçalho, sem decodificar pixels; máximo 8000 × 8000 → `400 IMAGE_DIMENSIONS_INVALID`
   (evita estouro de memória com imagem "bomba");
4. `PutObject` com chave `{pasta}/{uuid}.{jpg|png}`, `Content-Type` correto e
   `Cache-Control: public, max-age=31536000, immutable` (a chave nunca é reutilizada);
5. devolve a **URL pública completa**, gravada no banco:
   `https://objectstorage.{region}.oraclecloud.com/n/{namespace}/b/{bucket}/o/{chave}`.

| Uso | Pasta | Campo |
|---|---|---|
| Foto de produto (`POST`/`PUT /produtos`, part `file`) | `produtos/{empresaId}` | `Produto.foto` |
| Foto de perfil (`POST /user/foto`) | `usuarios/{userId}` | `User.fotoPerfil` |

Os apps (admin Flutter `Image.network(foto)`, App Cliente `imageUrl`/`photoUrl`) usam o campo direto
como URL; antes o backend gravava só o nome do arquivo e a imagem não carregava nos apps.

## Consistência com a transação

- Upload numa transação que sofre **rollback** → o objeto enviado é apagado (`afterCompletion`).
- Imagem **substituída** → a antiga é apagada só **depois do commit** (`deleteAfterCommit`), e só se a URL
  pertencer ao bucket configurado. Valores antigos (nome de arquivo do disco) e URLs externas são ignorados.
- Falha ao apagar só gera `WARN`; nunca afeta a requisição.

## Configuração

`application.properties` lê `OCI_NAMESPACE`, `OCI_REGION` (ex. `sa-saopaulo-1`), `OCI_BUCKET` (padrão
`app-imagens`), `OCI_ACCESS_KEY` e `OCI_SECRET_KEY` (Customer Secret Key do usuário OCI). O
`docker-compose.yml` repassa essas variáveis ao container. Sem elas a aplicação sobe, mas uploads
respondem `503 STORAGE_NOT_CONFIGURED`.

Cliente S3: endpoint `https://{namespace}.compat.objectstorage.{region}.oraclecloud.com`, path-style
obrigatório e envio chunked desligado (exigências da OCI).

**O bucket precisa ter visibilidade pública** (leitura de objetos), porque os apps carregam a URL sem
credencial. A chave é um UUID aleatório, então não é adivinhável; listagem do bucket não deve ser
liberada.

## Limites de upload

- `spring.servlet.multipart.max-file-size=10MB` / `max-request-size=11MB`: o maior upload é a planilha
  de importação de produtos (`product-import.max-file-bytes`, 10 MB). Acima disso → `413`.
- Imagens: 5 MB (`IMAGE_MAX_SIZE`). Foto de celular costuma ter 2–5 MB; para exibição em app e terminal,
  algo entre 1 e 2 MB já sobra, então o limite pode ser reduzido sem prejuízo.

## Pendências

- Migrar arquivos antigos do volume `uploads_data` para o bucket e atualizar `produto.foto`/
  `users.foto_perfil` que ainda guardam só o nome do arquivo (o volume não é mais montado, mas não foi apagado).
- Redimensionar/comprimir no upload (hoje a imagem é guardada como chegou).

Voltar para [[00-index]].
