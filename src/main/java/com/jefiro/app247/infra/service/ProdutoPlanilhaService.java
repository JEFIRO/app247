package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.dto.importacao.ImportacaoProdutoPayload;
import com.jefiro.app247.domain.model.enum_type.ProdutoCategoria;
import com.jefiro.app247.domain.model.enum_type.UnidadeMedida;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.*;

@Service
public class ProdutoPlanilhaService {
    static final String PRODUTOS = "PRODUTOS";
    static final String BARCODES = "CODIGOS_BARRAS";
    static final String ESTOQUE = "ESTOQUE_INICIAL";
    private static final List<String> PRODUTO_HEADERS = List.of("codigo_interno", "nome", "descricao",
            "preco_venda", "categoria", "unidade_medida", "peso", "peso_tolerancia", "ativo",
            "ncm", "cest", "origem_mercadoria", "gtin_tributavel", "unidade_tributavel");
    private static final List<String> BARCODE_HEADERS = List.of("codigo_interno", "codigo_barras", "tipo", "principal", "ativo");
    private static final List<String> ESTOQUE_HEADERS = List.of("codigo_interno", "quantidade_inicial", "motivo", "ativo");

    public byte[] modelo() {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet instrucoes = workbook.createSheet("INSTRUCOES");
            instrucoes.createRow(0).createCell(0).setCellValue("Importação App 24/7 — não altere os nomes das abas ou colunas.");
            instrucoes.createRow(1).createCell(0).setCellValue("PRODUTOS é obrigatória. Dados fiscais e estoque inicial são opcionais.");
            instrucoes.createRow(2).createCell(0).setCellValue("A aba INVENTARIO_EXEMPLO é somente documental e será ignorada.");
            criarAba(workbook, PRODUTOS, PRODUTO_HEADERS, List.of(
                    "COCA2L", "Coca-Cola 2L", "Refrigerante", "10,99", "Bebidas", "UN", "", "", "SIM",
                    "22021000", "", "0", "", "UN"));
            criarAba(workbook, BARCODES, BARCODE_HEADERS,
                    List.of("COCA2L", "7894900011517", "EAN13", "SIM", "SIM"));
            criarAba(workbook, ESTOQUE, ESTOQUE_HEADERS,
                    List.of("COCA2L", "100", "Carga inicial", "SIM"));
            criarAba(workbook, "INVENTARIO_EXEMPLO",
                    List.of("codigo_interno", "quantidade_contada", "observacao"),
                    List.of("COCA2L", "97", "Exemplo; esta aba não é importada"));
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Não foi possível gerar o modelo XLSX", ex);
        }
    }

    public Resultado ler(byte[] conteudo, int maxLinhas) {
        if (conteudo.length < 4 || conteudo[0] != 'P' || conteudo[1] != 'K') {
            throw new IllegalArgumentException("O arquivo não é um XLSX válido");
        }
        ZipSecureFile.setMinInflateRatio(0.01d);
        ZipSecureFile.setMaxEntrySize(50L * 1024 * 1024);
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(conteudo))) {
            if (!(workbook instanceof XSSFWorkbook)) throw new IllegalArgumentException("Envie uma planilha no formato XLSX");
            Sheet produtos = obrigatoria(workbook, PRODUTOS);
            Sheet barcodes = obrigatoria(workbook, BARCODES);
            Sheet estoque = obrigatoria(workbook, ESTOQUE);
            Map<String, Integer> hp = headers(produtos, PRODUTO_HEADERS, Set.of("codigo_interno", "nome", "preco_venda", "categoria", "unidade_medida"));
            Map<String, Integer> hb = headers(barcodes, BARCODE_HEADERS, Set.of("codigo_interno", "codigo_barras", "tipo", "principal", "ativo"));
            Map<String, Integer> he = headers(estoque, ESTOQUE_HEADERS, Set.of("codigo_interno", "quantidade_inicial", "motivo", "ativo"));

            List<LinhaProduto> linhasProdutos = new ArrayList<>();
            List<LinhaBarcode> linhasBarcodes = new ArrayList<>();
            List<LinhaEstoque> linhasEstoque = new ArrayList<>();
            int total = 0;
            for (int n = 1; n <= produtos.getLastRowNum(); n++) {
                Row row = produtos.getRow(n); if (vazia(row)) continue;
                total++; limite(total, maxLinhas);
                linhasProdutos.add(produto(row, hp));
            }
            for (int n = 1; n <= barcodes.getLastRowNum(); n++) {
                Row row = barcodes.getRow(n); if (vazia(row)) continue;
                total++; limite(total, maxLinhas);
                linhasBarcodes.add(barcode(row, hb));
            }
            for (int n = 1; n <= estoque.getLastRowNum(); n++) {
                Row row = estoque.getRow(n); if (vazia(row)) continue;
                total++; limite(total, maxLinhas);
                linhasEstoque.add(estoque(row, he));
            }
            return new Resultado(linhasProdutos, linhasBarcodes, linhasEstoque, total);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Não foi possível ler a planilha XLSX", ex);
        }
    }

    private LinhaProduto produto(Row row, Map<String, Integer> h) {
        List<String> erros = new ArrayList<>();
        String sku = texto(row, h, "codigo_interno", erros);
        String nome = texto(row, h, "nome", erros);
        BigDecimal preco = decimal(row, h, "preco_venda", 6, erros);
        String categoria = enumAmigavel(texto(row, h, "categoria", erros), ProdutoCategoria.class, true, "categoria", erros);
        String unidade = enumAmigavel(texto(row, h, "unidade_medida", erros), UnidadeMedida.class, false, "unidade_medida", erros);
        boolean ativo = bool(texto(row, h, "ativo", erros), true, "ativo", erros);
        BigDecimal peso = decimalOpcional(row, h, "peso", 3, erros);
        BigDecimal tolerancia = decimalOpcional(row, h, "peso_tolerancia", 3, erros);
        if (vazio(sku)) erros.add("codigo_interno obrigatório");
        if (vazio(nome)) erros.add("nome obrigatório");
        if (preco == null || preco.signum() <= 0) erros.add("preco_venda deve ser maior que zero");
        if (peso != null && peso.signum() < 0) erros.add("peso não pode ser negativo");
        if (tolerancia != null && tolerancia.signum() < 0) erros.add("peso_tolerancia não pode ser negativa");
        String ncm = texto(row, h, "ncm", erros);
        String cest = texto(row, h, "cest", erros);
        String origem = texto(row, h, "origem_mercadoria", erros);
        String gtin = texto(row, h, "gtin_tributavel", erros);
        String unidadeTrib = texto(row, h, "unidade_tributavel", erros);
        if (!vazio(ncm) && !ncm.matches("\\d{8}")) erros.add("ncm deve conter 8 dígitos");
        if (!vazio(cest) && !cest.matches("\\d{7}")) erros.add("cest deve conter 7 dígitos");
        if (!vazio(origem) && origem.length() > 2) erros.add("origem_mercadoria aceita no máximo 2 caracteres");
        if (!vazio(gtin) && (!gtin.matches("\\d{8,14}"))) erros.add("gtin_tributavel inválido");
        if (!vazio(unidadeTrib) && unidadeTrib.length() > 6) erros.add("unidade_tributavel aceita no máximo 6 caracteres");
        return new LinhaProduto(row.getRowNum() + 1, trim(sku), trim(nome), texto(row, h, "descricao", erros),
                preco, categoria, unidade, peso, tolerancia, ativo, trim(ncm), trim(cest), trim(origem),
                trim(gtin), trim(unidadeTrib), erros);
    }

    private LinhaBarcode barcode(Row row, Map<String, Integer> h) {
        List<String> erros = new ArrayList<>();
        String sku = trim(texto(row, h, "codigo_interno", erros));
        String codigo = trim(texto(row, h, "codigo_barras", erros));
        String tipo = normalizar(texto(row, h, "tipo", erros));
        boolean principal = bool(texto(row, h, "principal", erros), false, "principal", erros);
        boolean ativo = bool(texto(row, h, "ativo", erros), true, "ativo", erros);
        if (vazio(sku)) erros.add("codigo_interno obrigatório");
        if (vazio(codigo)) erros.add("codigo_barras obrigatório");
        if (vazio(tipo)) erros.add("tipo obrigatório");
        if (!vazio(codigo) && codigo.length() > 80) erros.add("codigo_barras excede 80 caracteres");
        if (("EAN13".equals(tipo) || "GTIN13".equals(tipo)) && !ean13Valido(codigo)) erros.add("EAN13 inválido");
        return new LinhaBarcode(row.getRowNum() + 1, sku, codigo, tipo, principal, ativo, erros);
    }

    private LinhaEstoque estoque(Row row, Map<String, Integer> h) {
        List<String> erros = new ArrayList<>();
        String sku = trim(texto(row, h, "codigo_interno", erros));
        BigDecimal quantidade = decimal(row, h, "quantidade_inicial", 3, erros);
        boolean ativo = bool(texto(row, h, "ativo", erros), true, "ativo", erros);
        if (vazio(sku)) erros.add("codigo_interno obrigatório");
        if (quantidade == null) erros.add("quantidade_inicial inválida");
        return new LinhaEstoque(row.getRowNum() + 1, sku, quantidade,
                texto(row, h, "motivo", erros), ativo, erros);
    }

    private Sheet obrigatoria(Workbook workbook, String nome) {
        Sheet sheet = workbook.getSheet(nome);
        if (sheet == null) throw new IllegalArgumentException("Aba obrigatória ausente: " + nome);
        return sheet;
    }

    private Map<String, Integer> headers(Sheet sheet, List<String> conhecidos, Set<String> obrigatorios) {
        Row row = sheet.getRow(0);
        if (row == null) throw new IllegalArgumentException("Cabeçalho ausente na aba " + sheet.getSheetName());
        Map<String, Integer> result = new HashMap<>();
        for (Cell cell : row) result.put(normalizarHeader(formatar(cell)), cell.getColumnIndex());
        for (String required : obrigatorios) if (!result.containsKey(required)) {
            throw new IllegalArgumentException("Coluna obrigatória ausente em " + sheet.getSheetName() + ": " + required);
        }
        for (String known : conhecidos) result.putIfAbsent(known, -1);
        return result;
    }

    private String texto(Row row, Map<String, Integer> h, String nome, List<String> erros) {
        int index = h.getOrDefault(nome, -1); if (index < 0) return null;
        Cell cell = row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return null;
        if (cell.getCellType() == CellType.FORMULA) {
            erros.add(nome + " não aceita fórmula"); return null;
        }
        return trim(formatar(cell));
    }

    private BigDecimal decimal(Row row, Map<String, Integer> h, String nome, int scale, List<String> erros) {
        String value = texto(row, h, nome, erros);
        if (vazio(value)) return null;
        try { return decimalBrasileiro(value).setScale(scale, RoundingMode.UNNECESSARY); }
        catch (RuntimeException ex) { erros.add(nome + " inválido"); return null; }
    }

    private BigDecimal decimalOpcional(Row row, Map<String, Integer> h, String nome, int scale, List<String> erros) {
        return decimal(row, h, nome, scale, erros);
    }

    private BigDecimal decimalBrasileiro(String value) {
        String n = value.replace(" ", "");
        if (n.contains(",") && n.contains(".")) n = n.replace(".", "").replace(',', '.');
        else if (n.contains(",")) n = n.replace(',', '.');
        return new BigDecimal(n);
    }

    private boolean bool(String value, boolean padrao, String campo, List<String> erros) {
        if (vazio(value)) return padrao;
        return switch (normalizar(value)) {
            case "SIM", "TRUE", "1", "ATIVO" -> true;
            case "NAO", "FALSE", "0", "INATIVO" -> false;
            default -> { erros.add(campo + " deve ser SIM ou NAO"); yield padrao; }
        };
    }

    private <E extends Enum<E>> String enumAmigavel(String value, Class<E> type, boolean singularizar,
                                                     String campo, List<String> erros) {
        if (vazio(value)) { erros.add(campo + " obrigatório"); return null; }
        String n = normalizar(value);
        try { return Enum.valueOf(type, n).name(); }
        catch (IllegalArgumentException ex) {
            if (singularizar && n.endsWith("S")) {
                try { return Enum.valueOf(type, n.substring(0, n.length() - 1)).name(); }
                catch (IllegalArgumentException ignored) { }
            }
            erros.add(campo + " inválido: " + value); return null;
        }
    }

    private boolean ean13Valido(String codigo) {
        if (codigo == null || !codigo.matches("\\d{13}")) return false;
        int soma = 0;
        for (int i = 0; i < 12; i++) soma += (codigo.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
        return (10 - soma % 10) % 10 == codigo.charAt(12) - '0';
    }

    private void criarAba(XSSFWorkbook workbook, String nome, List<String> headers, List<String> exemplo) {
        Sheet sheet = workbook.createSheet(nome);
        Row header = sheet.createRow(0);
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont(); font.setBold(true); style.setFont(font);
        for (int i = 0; i < headers.size(); i++) { Cell cell = header.createCell(i); cell.setCellValue(headers.get(i)); cell.setCellStyle(style); sheet.setColumnWidth(i, 22 * 256); }
        Row row = sheet.createRow(1);
        for (int i = 0; i < exemplo.size(); i++) row.createCell(i).setCellValue(exemplo.get(i));
    }

    private String formatar(Cell cell) { return new DataFormatter(new Locale("pt", "BR")).formatCellValue(cell); }
    private boolean vazia(Row row) { if (row == null) return true; for (Cell c : row) if (!vazio(formatar(c))) return false; return true; }
    private boolean vazio(String value) { return value == null || value.isBlank(); }
    private String trim(String value) { return vazio(value) ? null : value.trim(); }
    private String normalizarHeader(String value) { return normalizar(value).toLowerCase(Locale.ROOT); }
    private String normalizar(String value) { return value == null ? null : Normalizer.normalize(value.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "").replace(' ', '_').toUpperCase(Locale.ROOT); }
    private void limite(int total, int max) { if (total > max) throw new IllegalArgumentException("A planilha excede o limite de " + max + " linhas"); }

    public record Resultado(List<LinhaProduto> produtos, List<LinhaBarcode> barcodes,
                            List<LinhaEstoque> estoques, int totalLinhas) {}
    public record LinhaProduto(int linha, String sku, String nome, String descricao, BigDecimal preco,
                               String categoria, String unidade, BigDecimal peso, BigDecimal tolerancia,
                               boolean ativo, String ncm, String cest, String origem, String gtin,
                               String unidadeTributavel, List<String> erros) {}
    public record LinhaBarcode(int linha, String sku, String codigo, String tipo, boolean principal,
                               boolean ativo, List<String> erros) {}
    public record LinhaEstoque(int linha, String sku, BigDecimal quantidade, String motivo,
                               boolean ativo, List<String> erros) {}
}
