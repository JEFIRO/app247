# Importação de produtos por planilha

Voltar para [[00-index]]. Produto e estoque em [[database]] e [[estoque]].

## Fluxo e modo

A primeira versão aceita somente XLSX e usa `SOMENTE_NOVOS`: SKU ou barcode já existente na Empresa é erro; nenhum cadastro é sobrescrito. CSV era opcional e não foi incluído para manter um único modelo oficial e validação consistente.

```text
baixar modelo → upload/validação → preview → confirmação → job assíncrono → resultado
```

Validação não cria Produto. Ela persiste `importacao_produto` e linhas normalizadas em `importacao_produto_item`, sem conservar o arquivo bruto. Confirmação muda o estado para `PROCESSANDO`, publica evento após commit e devolve `202`. Cada Produto é importado em transação própria, atomicamente com barcodes, fiscal opcional, estoque inicial e movimento. O job finaliza como `CONCLUIDA`, `CONCLUIDA_COM_ERROS` ou `FALHOU`.

Uma linha já `IMPORTADO` não é reprocessada. Lock pessimista protege cada linha caso mais de um executor receba o mesmo trabalho; constraints de SKU/barcode e chaves do ledger são a última barreira. Confirmar novamente uma importação em processamento ou concluída não duplica dados. Um scheduler revisita em lote estados `PROCESSANDO`, permitindo recuperar trabalho após reinício; os intervalos `PRODUCT_IMPORT_RECOVERY_DELAY_MS` e `PRODUCT_IMPORT_RECOVERY_INITIAL_DELAY_MS` são configuráveis.

## Modelo XLSX oficial

`GET /produtos/importacao/modelo` gera a versão controlada pelo backend com cinco abas:

- `INSTRUCOES`;
- `PRODUTOS`;
- `CODIGOS_BARRAS`;
- `ESTOQUE_INICIAL`;
- `INVENTARIO_EXEMPLO`, exclusivamente documental e ignorada pelo importador.

### PRODUTOS

`codigo_interno`, `nome`, `descricao`, `preco_venda`, `categoria`, `unidade_medida`, `peso`, `peso_tolerancia`, `ativo`, `ncm`, `cest`, `origem_mercadoria`, `gtin_tributavel`, `unidade_tributavel`.

SKU, nome, preço, categoria e unidade são obrigatórios. Fiscal, peso e tolerância são opcionais. Preço aceita notação pt-BR ou decimal e é normalizado para seis casas; peso usa até três. Valores booleanos aceitam `SIM/NAO`, equivalentes normalizados e vazio usa o padrão documentado.

### CODIGOS_BARRAS

`codigo_interno`, `codigo_barras`, `tipo`, `principal`, `ativo`.

Um Produto pode ter vários códigos, mas precisa de exatamente um principal ativo entre os códigos informados. EAN/GTIN-13 valida quantidade de dígitos e dígito verificador. A unicidade é por Empresa, nunca global.

### ESTOQUE_INICIAL

`codigo_interno`, `quantidade_inicial`, `motivo`, `ativo`.

A aba é opcional por produto, embora sua estrutura esteja presente no modelo. Ela cria somente `EstoqueEmpresa`; não cria `EstoqueCondominio`. Saldo diferente de zero gera `CARGA_INICIAL_IMPORTACAO` com anterior zero, posterior exato e chave idempotente. Quantidade negativa continua permitida.

## Validação e segurança

São verificados: extensão e MIME, assinatura ZIP/XLSX, 10 MB e 10.000 linhas por padrão, abas/colunas, fórmulas, ZIP bomb, tipos, escalas, campos obrigatórios, referências entre abas, duplicidades no arquivo e no tenant e dados fiscais básicos. Os limites são configuráveis por `PRODUCT_IMPORT_MAX_FILE_BYTES` e `PRODUCT_IMPORT_MAX_ROWS`.

Fórmulas não são executadas e viram erro de linha. Macros não são aceitas pelo contrato `.xlsx`; recursos externos não são usados. Erros são coletados por linha e podem ser consultados sem stacktrace. Empresa sempre vem do JWT/`EmpresaContext`; não existe coluna de tenant na planilha.

## API e Flutter

- `GET /produtos/importacao/modelo`;
- `POST /produtos/importacao/validar` com part multipart `file`;
- `GET /produtos/importacao/{id}`;
- `POST /produtos/importacao/{id}/confirmar` (`202`);
- `GET /produtos/importacao/{id}/erros`.

`ImportacaoProdutoPage` implementa baixar modelo, selecionar arquivo, validar, revisar linhas, confirmar e acompanhar o job com polling limitado. A tela encerra o acompanhamento com sucesso, erro ou timeout recuperável; não mantém request de importação aberta. O relatório de erros é disponibilizado pela API como DTO; exportação para arquivo fica para evolução futura.

AuditLog registra validação, confirmação, conclusão/falha e cada Produto importado. Nenhum segredo ou arquivo bruto é persistido.
