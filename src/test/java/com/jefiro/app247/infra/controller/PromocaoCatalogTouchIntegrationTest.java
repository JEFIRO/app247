package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.Condominio;
import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.EstoqueCondominio;
import com.jefiro.app247.domain.model.Produto;
import com.jefiro.app247.domain.model.auth.RoleUser;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.enum_type.ProdutoCategoria;
import com.jefiro.app247.domain.model.enum_type.UnidadeMedida;
import com.jefiro.app247.infra.repository.CondominioRepository;
import com.jefiro.app247.infra.repository.EmpresaRepository;
import com.jefiro.app247.infra.repository.EstoqueCondominioRepository;
import com.jefiro.app247.infra.repository.ProdutoRepository;
import com.jefiro.app247.infra.repository.UserRepository;
import com.jefiro.app247.infra.service.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regressão: touchCatalog usa clearAutomatically e desanexava a promoção antes de montar a
 * resposta, gerando LazyInitializationException em Produto.codigosBarras. Sem @Transactional
 * de propósito: os dados precisam estar gravados para o service carregar coleções lazy reais.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PromocaoCatalogTouchIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired EmpresaRepository empresas;
    @Autowired CondominioRepository condominios;
    @Autowired ProdutoRepository produtos;
    @Autowired EstoqueCondominioRepository estoques;
    @Autowired UserRepository users;
    @Autowired TokenService tokens;

    private String bearer;
    private Produto produto;
    private String codigoBarras;

    @BeforeEach
    void setup() {
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        Empresa empresa = new Empresa();
        empresa.setRazaoSocial("Empresa Promo " + sufixo);
        empresa.setNomeFantasia("Empresa Promo " + sufixo);
        empresa.setCnpj(UUID.randomUUID().toString().substring(0, 18));
        empresa.setEmail("promo-" + sufixo + "@example.test");
        empresa.setAtivo(true);
        empresa = empresas.saveAndFlush(empresa);

        Condominio condominio = new Condominio();
        condominio.setNome("Condominio Promo " + sufixo);
        condominio.setEmpresa(empresa);
        condominio.setAtivo(true);
        condominio = condominios.saveAndFlush(condominio);

        codigoBarras = "789" + sufixo;
        Produto novo = new Produto();
        novo.setEmpresa(empresa);
        novo.setCodigoInterno("INT-" + sufixo);
        novo.setNome("Leite " + sufixo);
        novo.setPreco(BigDecimal.TEN);
        novo.setCategoria(ProdutoCategoria.OUTROS);
        novo.setUnidadeMedida(UnidadeMedida.UN);
        novo.setStatus(true);
        novo.adicionarCodigoBarras(codigoBarras, "EAN", true);
        produto = produtos.saveAndFlush(novo);

        EstoqueCondominio estoque = new EstoqueCondominio();
        estoque.setCondominio(condominio);
        estoque.setProduto(produto);
        estoque.setQuantidade(BigDecimal.TEN);
        estoque.alterarDisponibilidade(true);
        estoques.saveAndFlush(estoque);

        String cpf = String.format("%011d", Math.abs(UUID.randomUUID().getLeastSignificantBits() % 100_000_000_000L));
        User admin = users.saveAndFlush(User.builder().empresa(empresa).nome("Admin")
                .email("admin-promo-" + sufixo + "@example.test").senha("hash").cpf(cpf)
                .ativo(true).role(RoleUser.ADMIN).build());
        bearer = "Bearer " + tokens.generateToken(admin);
    }

    @Test
    void criarAtualizarEAlterarStatusRespondemComCodigoDoProduto() throws Exception {
        String id = com.jayway.jsonpath.JsonPath.read(mvc.perform(post("/promocoes")
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content(body("Promo")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.produtos[0].codigo").value(codigoBarras))
                .andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(put("/promocoes/{id}", id)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content(body("Promo editada")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.produtos[0].codigo").value(codigoBarras));

        mvc.perform(patch("/promocoes/{id}/status", id)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ativo\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.produtos[0].codigo").value(codigoBarras));
    }

    private String body(String nome) {
        Instant inicio = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant fim = Instant.now().plus(1, ChronoUnit.DAYS);
        return """
                {"nome":"%s","abrangencia":"EMPRESA","tipo":"PERCENTUAL","valor":10,
                 "inicio":"%s","fim":"%s","ativo":true,"produtoIds":["%s"]}
                """.formatted(nome, inicio, fim, produto.getIdProduto());
    }
}
