package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.Condominio;
import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.EstoqueCondominio;
import com.jefiro.app247.domain.model.Produto;
import com.jefiro.app247.domain.model.ProdutoCodigoBarras;
import com.jefiro.app247.domain.model.dto.CreateProductDTO;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class ProductCatalogRepositoryTest {
    @Autowired EntityManager entityManager;
    @Autowired EstoqueCondominioRepository estoqueCondominioRepository;

    @Test
    void mesmoCodigoDeBarrasPodeExistirEmEmpresasDiferentes() {
        Empresa a = empresa("A", "10101010000110", "catalog-a@teste.com");
        Empresa b = empresa("B", "20202020000120", "catalog-b@teste.com");

        produto(a, "A-001", "7894900011517");
        produto(b, "B-001", "7894900011517");

        entityManager.flush();
        assertThat(entityManager.createQuery(
                        "select count(c) from ProdutoCodigoBarras c where c.codigoBarras=:codigo", Long.class)
                .setParameter("codigo", "7894900011517")
                .getSingleResult()).isEqualTo(2);
    }

    @Test
    void codigoDeBarrasDuplicadoNaMesmaEmpresaViolaRestricaoDoBanco() {
        Empresa empresa = empresa("C", "30303030000130", "catalog-c@teste.com");
        produto(empresa, "C-001", "7894900011524");
        produto(empresa, "C-002", "7894900011524");

        assertThatThrownBy(entityManager::flush)
                .isInstanceOf(org.hibernate.exception.ConstraintViolationException.class);
    }

    @Test
    void disponibilidadeListaSomenteCondominiosAtivosDoTenantEAssociaçãoReal() {
        Empresa a = empresa("D", "40404040000140", "catalog-d@teste.com");
        Empresa b = empresa("E", "50505050000150", "catalog-e@teste.com");
        Produto produto = produto(a, "D-001", "7894900011531");
        Condominio associado = condominio(a, "Residencial associado", true);
        condominio(a, "Residencial disponível", true);
        condominio(a, "Residencial inativo", false);
        condominio(b, "Outro tenant", true);
        EstoqueCondominio estoque = new EstoqueCondominio();
        estoque.setEmpresa(a);
        estoque.setCondominio(associado);
        estoque.setProduto(produto);
        estoque.setQuantidade(new BigDecimal("8.000"));
        estoque.setAtivo(true);
        entityManager.persist(estoque);
        entityManager.flush();

        var resultado = estoqueCondominioRepository.findProductAvailability(a.getId(), produto.getIdProduto());

        assertThat(resultado).hasSize(2);
        assertThat(resultado).anySatisfy(item -> {
            assertThat(item.condominioNome()).isEqualTo("Residencial associado");
            assertThat(item.associado()).isTrue();
            assertThat(item.quantidade()).isEqualByComparingTo("8.000");
        });
        assertThat(resultado).anySatisfy(item -> {
            assertThat(item.condominioNome()).isEqualTo("Residencial disponível");
            assertThat(item.associado()).isFalse();
            assertThat(item.quantidade()).isNull();
        });
    }

    private Empresa empresa(String suffix, String cnpj, String email) {
        Empresa empresa = Empresa.builder()
                .razaoSocial("Empresa " + suffix).nomeFantasia("Empresa " + suffix)
                .cnpj(cnpj).email(email).tenantId("tenant-catalog-" + suffix)
                .ativo(true).build();
        entityManager.persist(empresa);
        return empresa;
    }

    private Produto produto(Empresa empresa, String sku, String barcode) {
        CreateProductDTO dto = new CreateProductDTO(
                barcode, "Produto " + sku, BigDecimal.TEN, null,
                "UN", "OUTROS", null, null, BigDecimal.ONE, BigDecimal.ZERO);
        Produto produto = new Produto(dto);
        produto.setCodigoInterno(sku);
        produto.setEmpresa(empresa);
        ProdutoCodigoBarras codigo = produto.getCodigosBarras().isEmpty()
                ? null : produto.getCodigosBarras().get(0);
        if (codigo == null) produto.adicionarCodigoBarras(barcode, "EAN", true);
        entityManager.persist(produto);
        return produto;
    }

    private Condominio condominio(Empresa empresa, String nome, boolean ativo) {
        Condominio condominio = new Condominio();
        condominio.setEmpresa(empresa);
        condominio.setNome(nome);
        condominio.setAtivo(ativo);
        entityManager.persist(condominio);
        return condominio;
    }
}
