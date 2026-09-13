package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.*;
import com.jefiro.app247.domain.model.dto.estoque.*;
import com.jefiro.app247.domain.model.enum_type.*;
import com.jefiro.app247.infra.repository.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {"spring.task.scheduling.enabled=false", "payment.reconciliation.startup.enabled=false"})
@ActiveProfiles("test")
class InventoryAndProductImportIntegrationTest {
    @Autowired InventarioService inventarioService;
    @Autowired EstoqueEmpresaService estoqueEmpresaService;
    @Autowired PlanogramaService planogramaService;
    @Autowired ProdutoPlanilhaService planilhaService;
    @Autowired ProdutoImportacaoService importacaoService;
    @Autowired ProdutoImportacaoProcessor importacaoProcessor;
    @Autowired EmpresaRepository empresaRepository;
    @Autowired CondominioRepository condominioRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired ProdutoCodigoBarrasRepository barcodeRepository;
    @Autowired EstoqueEmpresaRepository estoqueRepository;
    @Autowired MovimentacaoEstoqueRepository movimentoRepository;

    @AfterEach void clear() { EmpresaContext.clear(); }

    @Test
    void inventarioAplicaFaltaSobraENaoCriaMovimentoParaSaldoIgual() {
        Fixture falta = fixture("100.000");
        var inventarioFalta = iniciarEContar(falta, "97.000");
        var finalizadoFalta = inventarioService.finalizar(inventarioFalta.id());
        assertThat(finalizadoFalta.status()).isEqualTo(InventarioStatus.FINALIZADO);
        assertThat(saldo(falta)).isEqualByComparingTo("97.000");
        assertThat(movimentosInventario(inventarioFalta.id())).singleElement()
                .extracting(MovimentacaoEstoque::getQuantidade).isEqualTo(new BigDecimal("-3.000"));

        Fixture sobra = fixture("100.000");
        var inventarioSobra = iniciarEContar(sobra, "103.000");
        inventarioService.finalizar(inventarioSobra.id());
        assertThat(saldo(sobra)).isEqualByComparingTo("103.000");
        assertThat(movimentosInventario(inventarioSobra.id()).get(0).getQuantidade()).isEqualByComparingTo("3.000");

        Fixture igual = fixture("100.000");
        var inventarioIgual = iniciarEContar(igual, "100.000");
        inventarioService.finalizar(inventarioIgual.id());
        assertThat(movimentosInventario(inventarioIgual.id())).isEmpty();
        assertThat(inventarioService.buscar(inventarioIgual.id()).itens().get(0).status()).isEqualTo(InventarioItemStatus.OK);
    }

    @Test
    void finalizarDuasVezesPermaneceIdempotenteECancelarNaoAlteraSaldo() {
        Fixture fixture = fixture("100.000");
        var inventario = iniciarEContar(fixture, "97.000");
        inventarioService.finalizar(inventario.id());
        inventarioService.finalizar(inventario.id());
        assertThat(movimentosInventario(inventario.id())).hasSize(1);

        Fixture cancelado = fixture("50.000");
        var outro = inventarioService.iniciar(new InventarioRequest(
                TipoLocalEstoque.ESTOQUE_EMPRESA, null, "cancelar", null, false));
        inventarioService.cancelar(outro.id());
        assertThat(saldo(cancelado)).isEqualByComparingTo("50.000");
        assertThat(movimentosInventario(outro.id())).isEmpty();
    }

    @Test
    void mudancaDepoisDoSnapshotGeraConflitoERecontagemProtegeMovimentoLegitimo() {
        Fixture fixture = fixture("100.000");
        var inventario = iniciarEContar(fixture, "97.000");
        estoqueEmpresaService.entrada(fixture.produto().getIdProduto(), new BigDecimal("20.000"), "transferência concorrente");

        var conflito = inventarioService.finalizar(inventario.id());
        assertThat(conflito.status()).isEqualTo(InventarioStatus.COM_CONFLITO);
        assertThat(conflito.itens().get(0).saldoAtualConflito()).isEqualByComparingTo("120.000");
        assertThat(saldo(fixture)).isEqualByComparingTo("120.000");
        assertThat(movimentosInventario(inventario.id())).isEmpty();

        inventarioService.contar(inventario.id(), conflito.itens().get(0).id(),
                new ContagemInventarioRequest(new BigDecimal("117.000"), "recontagem"));
        var finalizado = inventarioService.finalizar(inventario.id());
        assertThat(finalizado.status()).isEqualTo(InventarioStatus.FINALIZADO);
        assertThat(saldo(fixture)).isEqualByComparingTo("117.000");
        assertThat(movimentosInventario(inventario.id()).get(0).getQuantidade()).isEqualByComparingTo("-3.000");
    }

    @Test
    void contagemCegaOcultaSaldoEPlanogramaSomenteInformaLocal() {
        Fixture fixture = fixture("10.000");
        var planograma = planogramaService.criar(new PlanogramaRequest("Central inventário", null, true));
        var posicionado = planogramaService.adicionarPosicao(planograma.id(), new PlanogramaPosicaoRequest(
                "Bebidas", "A", "02", null, "03", "01", 1, null, null, null, null));
        planogramaService.adicionarProduto(posicionado.posicoes().get(0).id(),
                new PlanogramaProdutoRequest(fixture.produto().getIdProduto(), 1, null, null, null, true));
        var inventario = inventarioService.iniciar(new InventarioRequest(
                TipoLocalEstoque.ESTOQUE_EMPRESA, null, "cego", null, true));
        assertThat(inventario.itens().get(0).saldoSistemaSnapshot()).isNull();
        assertThat(inventario.itens().get(0).localizacaoPlanograma()).isEqualTo("A-02-03-01");
        assertThat(saldo(fixture)).isEqualByComparingTo("10.000");
    }

    @Test
    void inventarioBloqueiaCondominioDeOutroTenant() {
        Fixture atual = fixture("1.000");
        Fixture outro = fixture("1.000");
        EmpresaContext.set(atual.empresa().getId());
        assertThatThrownBy(() -> inventarioService.iniciar(new InventarioRequest(
                TipoLocalEstoque.CONDOMINIO, outro.condominio().getIdCondominio(), null, null, false)))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void modeloOficialEhValidoEImportacaoCriaProdutoBarcodesEstoqueEMovimento() throws Exception {
        Empresa empresa = empresa(); EmpresaContext.set(empresa.getId());
        byte[] comMultiplosBarcodes = alterar(planilhaService.modelo(), workbook -> {
            var barcodes = workbook.getSheet("CODIGOS_BARRAS");
            var alternativo = barcodes.createRow(2);
            alternativo.createCell(0).setCellValue("COCA2L");
            alternativo.createCell(1).setCellValue("7894900011524");
            alternativo.createCell(2).setCellValue("EAN13");
            alternativo.createCell(3).setCellValue("NAO");
            alternativo.createCell(4).setCellValue("SIM");
        });
        var validada = importacaoService.validar(arquivo(comMultiplosBarcodes));
        assertThat(validada.totalValidas()).isEqualTo(1);
        assertThat(validada.totalErros()).isZero();
        importacaoService.confirmar(validada.importacaoId());
        importacaoProcessor.processar(validada.importacaoId());
        var concluida = importacaoService.buscar(validada.importacaoId());
        assertThat(concluida.status()).isIn(ImportacaoProdutoStatus.CONCLUIDA, ImportacaoProdutoStatus.PROCESSANDO);
        aguardarConclusao(validada.importacaoId());
        Produto produto = produtoRepository.findByCodigoInternoAndEmpresaId("COCA2L", empresa.getId()).orElseThrow();
        assertThat(produto.getPreco()).isEqualByComparingTo("10.990000");
        assertThat(barcodeRepository.findAllByProdutoIdProdutoOrderByPrincipalDescCreatedAtAsc(produto.getIdProduto()))
                .hasSize(2).first().extracting(ProdutoCodigoBarras::getPrincipal).isEqualTo(true);
        EstoqueEmpresa estoque = estoqueRepository.findByEmpresaIdAndProdutoIdProduto(empresa.getId(), produto.getIdProduto()).orElseThrow();
        assertThat(estoque.getQuantidade()).isEqualByComparingTo("100.000");
        assertThat(movimentoRepository.findAllByEstoqueEmpresaEmpresaIdOrderByCreatedAtDesc(empresa.getId()))
                .anyMatch(m -> m.getTipo() == TipoMovimentacaoEstoque.CARGA_INICIAL_IMPORTACAO
                        && m.getQuantidade().compareTo(new BigDecimal("100.000")) == 0);
    }

    @Test
    void validacaoColetaSkuEBarcodeDuplicadosSemPersistirProduto() throws Exception {
        Empresa empresa = empresa(); EmpresaContext.set(empresa.getId());
        byte[] duplicada = alterar(planilhaService.modelo(), workbook -> {
            var produtos = workbook.getSheet("PRODUTOS");
            var row = produtos.createRow(2);
            for (int i = 0; i < produtos.getRow(1).getLastCellNum(); i++)
                row.createCell(i).setCellValue(produtos.getRow(1).getCell(i).getStringCellValue());
            var barcodes = workbook.getSheet("CODIGOS_BARRAS");
            var barcode = barcodes.createRow(2);
            barcode.createCell(0).setCellValue("COCA2L"); barcode.createCell(1).setCellValue("7894900011517");
            barcode.createCell(2).setCellValue("EAN13"); barcode.createCell(3).setCellValue("NAO"); barcode.createCell(4).setCellValue("SIM");
        });
        var result = importacaoService.validar(arquivo(duplicada));
        assertThat(result.totalErros()).isGreaterThanOrEqualTo(2);
        assertThat(result.preview()).anyMatch(l -> l.mensagem().contains("duplicado"));
        assertThat(produtoRepository.findByCodigoInternoAndEmpresaId("COCA2L", empresa.getId())).isEmpty();
        assertThatThrownBy(() -> importacaoService.confirmar(result.importacaoId())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void skuEBarcodeExistentesNoTenantSaoErroMasMesmoBarcodeEmOutroTenantEhPermitido() throws Exception {
        Empresa primeira = empresa(); EmpresaContext.set(primeira.getId());
        var primeiraImportacao = importacaoService.validar(arquivo(planilhaService.modelo()));
        importacaoService.confirmar(primeiraImportacao.importacaoId());
        importacaoProcessor.processar(primeiraImportacao.importacaoId());
        aguardarConclusao(primeiraImportacao.importacaoId());
        var repetida = importacaoService.validar(arquivo(planilhaService.modelo()));
        assertThat(repetida.totalErros()).isPositive();
        assertThat(repetida.preview()).anyMatch(l -> l.mensagem().contains("já existe"));

        Empresa segunda = empresa(); EmpresaContext.set(segunda.getId());
        var outroTenant = importacaoService.validar(arquivo(planilhaService.modelo()));
        assertThat(outroTenant.totalErros()).isZero();
    }

    @Test
    void referenciasInexistentesPrecoInvalidoEAbasOuColunasAusentesSaoRejeitados() throws Exception {
        Empresa empresa = empresa(); EmpresaContext.set(empresa.getId());
        byte[] referencias = alterar(planilhaService.modelo(), workbook -> {
            workbook.getSheet("PRODUTOS").getRow(1).getCell(3).setCellValue("invalido");
            workbook.getSheet("CODIGOS_BARRAS").getRow(1).getCell(0).setCellValue("NAO_EXISTE");
            workbook.getSheet("ESTOQUE_INICIAL").getRow(1).getCell(0).setCellValue("NAO_EXISTE");
        });
        var result = importacaoService.validar(arquivo(referencias));
        assertThat(result.preview()).anyMatch(l -> l.mensagem().contains("preco_venda"));
        assertThat(result.preview().stream().filter(l -> l.mensagem().contains("não existe"))).hasSizeGreaterThanOrEqualTo(2);

        byte[] semAba = alterar(planilhaService.modelo(), workbook -> workbook.removeSheetAt(workbook.getSheetIndex("CODIGOS_BARRAS")));
        assertThatThrownBy(() -> importacaoService.validar(arquivo(semAba)))
                .hasMessageContaining("Aba obrigatória ausente");
        byte[] semColuna = alterar(planilhaService.modelo(), workbook -> workbook.getSheet("PRODUTOS").getRow(0).getCell(0).setCellValue("outra_coluna"));
        assertThatThrownBy(() -> importacaoService.validar(arquivo(semColuna)))
                .hasMessageContaining("Coluna obrigatória ausente");

        var arquivoInvalido = new MockMultipartFile("file", "produtos.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "não é um xlsx".getBytes());
        assertThatThrownBy(() -> importacaoService.validar(arquivoInvalido))
                .isInstanceOfAny(IllegalArgumentException.class, java.io.IOException.class);
    }

    @Test
    void confirmacaoRepetidaNaoDuplicaProdutoNemEstoque() throws Exception {
        Empresa empresa = empresa(); EmpresaContext.set(empresa.getId());
        var importacao = importacaoService.validar(arquivo(planilhaService.modelo()));
        importacaoService.confirmar(importacao.importacaoId());
        importacaoService.confirmar(importacao.importacaoId());
        importacaoProcessor.processar(importacao.importacaoId());
        aguardarConclusao(importacao.importacaoId());
        importacaoService.confirmar(importacao.importacaoId());
        assertThat(produtoRepository.findAllByEmpresaId(empresa.getId(), org.springframework.data.domain.Pageable.unpaged()))
                .hasSize(1);
        assertThat(estoqueRepository.findAll()).hasSizeGreaterThanOrEqualTo(1);
    }

    private InventarioResponse iniciarEContar(Fixture fixture, String quantidade) {
        EmpresaContext.set(fixture.empresa().getId());
        var inventario = inventarioService.iniciar(new InventarioRequest(
                TipoLocalEstoque.ESTOQUE_EMPRESA, null, "contagem", null, false));
        return inventarioService.contar(inventario.id(), inventario.itens().get(0).id(),
                new ContagemInventarioRequest(new BigDecimal(quantidade), "contagem física"));
    }
    private List<MovimentacaoEstoque> movimentosInventario(String id) { return movimentoRepository.findAllByInventarioIdOrderByCreatedAtAsc(id); }
    private BigDecimal saldo(Fixture f) { return estoqueRepository.findByEmpresaIdAndProdutoIdProduto(f.empresa().getId(), f.produto().getIdProduto()).orElseThrow().getQuantidade(); }
    private Fixture fixture(String saldo) {
        Empresa empresa = empresa(); EmpresaContext.set(empresa.getId());
        Condominio condominio = new Condominio(); condominio.setEmpresa(empresa); condominio.setNome("Condomínio " + suffix()); condominio.setAtivo(true);
        condominio = condominioRepository.saveAndFlush(condominio);
        Produto produto = produto(empresa, "SKU-" + suffix());
        estoqueEmpresaService.entrada(produto.getIdProduto(), new BigDecimal(saldo), "inicial");
        return new Fixture(empresa, condominio, produto);
    }
    private Empresa empresa() {
        String suffix = suffix();
        return empresaRepository.saveAndFlush(Empresa.builder().razaoSocial("Empresa " + suffix)
                .nomeFantasia("Empresa " + suffix).cnpj("CNPJ-" + suffix).email(suffix + "@test.local").ativo(true).build());
    }
    private Produto produto(Empresa empresa, String sku) {
        Produto produto = new Produto(); produto.setEmpresa(empresa); produto.setCodigoInterno(sku); produto.setNome("Produto " + sku);
        produto.setPreco(new BigDecimal("10.990000")); produto.setCategoria(ProdutoCategoria.OUTROS);
        produto.setUnidadeMedida(UnidadeMedida.UN); produto.setStatus(true); return produtoRepository.saveAndFlush(produto);
    }
    private MockMultipartFile arquivo(byte[] bytes) { return new MockMultipartFile("file", "produtos.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes); }
    private byte[] alterar(byte[] source, Consumer<XSSFWorkbook> action) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(source)); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            action.accept(workbook); workbook.write(out); return out.toByteArray();
        }
    }
    private void aguardarConclusao(String id) {
        for (int i = 0; i < 50; i++) {
            var status = importacaoService.buscar(id).status();
            if (status == ImportacaoProdutoStatus.CONCLUIDA || status == ImportacaoProdutoStatus.CONCLUIDA_COM_ERROS) return;
            try { Thread.sleep(20); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new RuntimeException(ex); }
        }
        fail("Importação não concluiu");
    }
    private String suffix() { return UUID.randomUUID().toString().substring(0, 8); }
    private record Fixture(Empresa empresa, Condominio condominio, Produto produto) {}
}
