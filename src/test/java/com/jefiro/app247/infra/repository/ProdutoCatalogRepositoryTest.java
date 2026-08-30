package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.Condominio;
import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.EstoqueCondominio;
import com.jefiro.app247.domain.model.Produto;
import com.jefiro.app247.domain.model.terminal.Terminal;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ProdutoCatalogRepositoryTest {
    @Autowired EntityManager entityManager;
    @Autowired EstoqueCondominioRepository estoqueRepository;
    @Autowired TerminalRepository terminalRepository;

    @Test
    void queryGlobalAtingeDoisCondominiosERemocaoContinuaVisivel() {
        Empresa empresa = empresa();
        Condominio condA = condominio("A", empresa);
        Condominio condB = condominio("B", empresa);
        Terminal terminalA = terminal("A1", condA);
        Terminal terminalB = terminal("B1", condB);
        Produto produto = produto(empresa);
        EstoqueCondominio estoqueA = estoque(condA, produto);
        EstoqueCondominio estoqueB = estoque(condB, produto);
        entityManager.flush();

        LocalDateTime antesDaAtualizacao = LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1);
        produto.setPreco(BigDecimal.valueOf(8));
        entityManager.flush();
        LocalDateTime depoisDaAtualizacao = LocalDateTime.now(ZoneOffset.UTC).plusSeconds(1);

        assertThat(estoqueRepository.findActiveCondominiumIdsByProductId(produto.getIdProduto()))
                .containsExactlyInAnyOrder(condA.getIdCondominio(), condB.getIdCondominio());
        assertThat(estoqueRepository.findCatalogChanges(
                condA.getIdCondominio(), antesDaAtualizacao, depoisDaAtualizacao))
                .extracting(e -> e.getProduto().getIdProduto()).containsExactly(produto.getIdProduto());
        assertThat(estoqueRepository.findCatalogChanges(
                condB.getIdCondominio(), antesDaAtualizacao, depoisDaAtualizacao))
                .extracting(e -> e.getProduto().getIdProduto()).containsExactly(produto.getIdProduto());
        assertThat(terminalRepository.findIdsByCondominiumIds(Set.of(condA.getIdCondominio())))
                .containsExactly(terminalA.getIdTerminal())
                .doesNotContain(terminalB.getIdTerminal());

        LocalDateTime antesDaRemocao = LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1);
        estoqueA.alterarDisponibilidade(false);
        entityManager.flush();

        assertThat(estoqueRepository.findCurrentCatalog(condA.getIdCondominio())).isEmpty();
        assertThat(estoqueRepository.findCatalogChanges(
                condA.getIdCondominio(), antesDaRemocao, LocalDateTime.now(ZoneOffset.UTC).plusSeconds(1)))
                .singleElement().satisfies(e -> assertThat(e.getAtivo()).isFalse());
        assertThat(estoqueB.getAtivo()).isTrue();
    }

    @Test
    void incrementalEncontraAssociacaoNovaMesmoComProdutoAnteriorAoCursor() {
        Empresa empresa = empresa();
        Condominio condominio = condominio("A", empresa);
        Produto produto = produto(empresa);
        entityManager.flush();
        LocalDateTime cursor = LocalDateTime.now(ZoneOffset.UTC);

        EstoqueCondominio estoque = estoque(condominio, produto);
        entityManager.flush();

        assertThat(produto.getUpdateAt()).isBeforeOrEqualTo(cursor);
        assertThat(estoque.getUpdatedAt()).isAfter(cursor);
        assertThat(estoqueRepository.findCatalogChanges(
                condominio.getIdCondominio(), cursor,
                LocalDateTime.now(ZoneOffset.UTC).plusSeconds(1)))
                .extracting(e -> e.getProduto().getIdProduto())
                .containsExactly(produto.getIdProduto());
        assertThat(estoqueRepository.findCurrentCatalog(condominio.getIdCondominio()))
                .singleElement().satisfies(e -> {
                    assertThat(e.getProduto().getIdProduto()).isEqualTo(produto.getIdProduto());
                    assertThat(e.getQuantidade()).isEqualByComparingTo("-1");
                });
    }

    private Empresa empresa() {
        Empresa empresa = Empresa.builder()
                .razaoSocial("Empresa A").nomeFantasia("Empresa A")
                .cnpj("11111111000111").email("catalogo@email.com")
                .tenantId("tenant-catalogo").ativo(true).build();
        entityManager.persist(empresa);
        return empresa;
    }

    private Condominio condominio(String nome, Empresa empresa) {
        Condominio condominio = new Condominio();
        condominio.setNome("Condomínio " + nome);
        condominio.setEmpresa(empresa);
        condominio.setAtivo(true);
        entityManager.persist(condominio);
        return condominio;
    }

    private Terminal terminal(String nome, Condominio condominio) {
        Terminal terminal = new Terminal();
        terminal.setNome(nome);
        terminal.setCondominio(condominio);
        terminal.setAtivo(true);
        entityManager.persist(terminal);
        return terminal;
    }

    private Produto produto(Empresa empresa) {
        Produto produto = new Produto();
        produto.setEmpresa(empresa);
        produto.setCodigo("789");
        produto.setNome("Leite");
        produto.setPreco(BigDecimal.valueOf(7));
        produto.setStatus(true);
        entityManager.persist(produto);
        return produto;
    }

    private EstoqueCondominio estoque(Condominio condominio, Produto produto) {
        EstoqueCondominio estoque = new EstoqueCondominio();
        estoque.setCondominio(condominio);
        estoque.setProduto(produto);
        estoque.setQuantidade(BigDecimal.valueOf(-1));
        estoque.alterarDisponibilidade(true);
        entityManager.persist(estoque);
        return estoque;
    }
}
