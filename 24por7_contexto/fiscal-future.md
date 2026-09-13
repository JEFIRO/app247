# Preparação fiscal — futura NFC-e

Esta etapa não emite NFC-e. Não cria certificado, assinatura XML, CSC, QR Code fiscal, contingência ou integração SEFAZ.

## Fontes revisadas

O desenho foi conferido no Portal Nacional da NF-e, especialmente o MOC 7.0/Anexo I e as notas técnicas vigentes da Reforma Tributária do Consumo. O leiaute distingue código comercial/GTIN, NCM, unidade comercial e unidade/GTIN tributável. As notas técnicas de 2025/2026 continuam alterando grupos e validações do IBS/CBS; por isso esses tributos não são congelados como colunas rígidas do cadastro comercial.

- Portal/MOC: <https://www.nfe.fazenda.gov.br/portal/listaConteudo.aspx?tipoConteudo=ndIjl+iEFdE=>
- Notas técnicas vigentes: <https://www.nfe.fazenda.gov.br/portal/listaConteudo.aspx?tipoConteudo=6WfrpZYE4Ik=>

## Separação adotada

- `produto`: dados comerciais e SKU interno;
- `produto_codigo_barras`: vários identificadores comerciais por produto e empresa;
- `produto_fiscal`: NCM, CEST, origem, unidade e GTIN tributáveis, fator de conversão;
- `perfil_tributario`: ponto de extensão tenant-aware para o futuro motor por contexto de operação;
- `order_item.fiscal_snapshot`: envelope JSON opcional e versionado quando o módulo fiscal existir.

CFOP, CST/CSOSN, alíquotas e classificações IBS/CBS não são fontes fixas em Produto: dependem de regime, destinatário, UF, finalidade, data e operação. A futura emissão deverá gerar e congelar o snapshot fiscal da venda sem consultar retroativamente o Produto alterado.

`NCM` possui uma fonte única: `produto_fiscal.ncm`. Não existe `produto.ncm` nem `perfil_tributario.ncm`.
