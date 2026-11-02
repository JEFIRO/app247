package com.jefiro.app247.infra.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jefiro.app247.domain.model.*;
import com.jefiro.app247.domain.model.appclient.CustomerCoupon;
import com.jefiro.app247.domain.model.appclient.AppClienteConfig;
import com.jefiro.app247.domain.model.appclient.LoyaltyAccount;
import com.jefiro.app247.domain.model.appclient.Reward;
import com.jefiro.app247.domain.model.appclient.RewardDiscountType;
import com.jefiro.app247.domain.model.auth.Endereco;
import com.jefiro.app247.domain.model.auth.RoleUser;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.enum_type.*;
import com.jefiro.app247.domain.model.enum_type.order.OrderStatus;
import com.jefiro.app247.domain.model.terminal.Terminal;
import com.jefiro.app247.infra.repository.*;
import com.jefiro.app247.infra.service.AppClienteService;
import com.jefiro.app247.infra.service.TokenService;
import com.jefiro.app247.infra.service.comprovante.ComprovanteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Integração HTTP -> Security -> service real -> repositories reais. */
@SpringBootTest(properties = {
        "spring.jpa.open-in-view=false",
        "spring.task.scheduling.enabled=false",
        "payment.reconciliation.startup.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AppClienteEndpointsIntegrationTest {
    private static final java.util.concurrent.atomic.AtomicLong CPF_SEQUENCE =
            new java.util.concurrent.atomic.AtomicLong(10000000000L);
    @Autowired MockMvc mvc;
    @Autowired AppClienteService appClienteService;
    @Autowired TokenService tokens;
    @Autowired EmpresaRepository empresas;
    @Autowired CondominioRepository condominios;
    @Autowired UserRepository users;
    @Autowired TerminalRepository terminals;
    @Autowired ProdutoRepository produtos;
    @Autowired EstoqueCondominioRepository estoques;
    @Autowired PromocaoRepository promocoes;
    @Autowired CarrinhoRepository carrinhos;
    @Autowired OrderRepository orders;
    @Autowired OrderItemRepository orderItems;
    @Autowired EnderecoRepository enderecos;
    @Autowired PagamentoRepository pagamentos;
    @Autowired LoyaltyAccountRepository accounts;
    @Autowired RewardRepository rewards;
    @Autowired CustomerCouponRepository coupons;
    @Autowired AppClienteConfigRepository appConfigs;
    @Autowired CustomerIdentificationTokenRepository identificationTokens;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean StringRedisTemplate redis;
    @MockitoBean RedisTemplate<String, Object> redisTemplate;
    @MockitoBean ComprovanteService comprovantes;

    private String authorization;
    private String orderId;
    private String rewardId;
    private String couponId;
    private Empresa empresa;
    private Condominio condominio;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ValueOperations<String, String> values = org.mockito.Mockito.mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        ListOperations<String, Object> lists = org.mockito.Mockito.mock(ListOperations.class);
        when(redisTemplate.opsForList()).thenReturn(lists);

        String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        empresa = empresa("Empresa " + suffix, "10" + digits(suffix, 12), suffix + "@empresa.test");
        condominio = condominio(empresa, "Condomínio principal " + suffix);
        User user = user(empresa, condominio, nextCpf(), suffix + "@cliente.test", RoleUser.USER);
        authorization = "Bearer " + tokens.generateToken(user);

        AppClienteConfig config = AppClienteConfig.defaults(empresa);
        config.setCatalogo(true); config.setPromocoes(true); config.setFidelidade(true);
        config.setQrIdentificacao(true); config.setPreferencias(true); config.setSuporte(true);
        config.setLoyaltyMode(AppClienteConfig.LoyaltyMode.POINTS);
        config.setPointsPerCurrency(BigDecimal.ONE);
        config.synchronizeDerivedFlags();
        appConfigs.saveAndFlush(config);

        Produto produto = produto(empresa, "SKU-" + suffix, "Água", true);
        estoque(condominio, produto, true);
        promocao(condominio, produto);

        Terminal terminal = new Terminal();
        terminal.setCondominio(condominio);
        terminal.setNome("Terminal " + suffix);
        terminal.setCodigo("T-" + suffix);
        terminal.setStatus(TerminalStatus.ONLINE);
        terminal = terminals.saveAndFlush(terminal);

        Carrinho cart = new Carrinho();
        cart.setTerminal(terminal);
        cart.setStatus(CarrinhoStatus.PAID);
        cart.setSubtotal(new BigDecimal("8.000000"));
        cart = carrinhos.saveAndFlush(cart);

        Order order = new Order();
        order.setEmpresa(empresa);
        order.setCondominio(condominio);
        order.setTerminal(terminal);
        order.setCarrinho(cart);
        order.setUser(user);
        order.setSubtotal(new BigDecimal("10.000000"));
        order.setDesconto(new BigDecimal("2.000000"));
        order.setTotalCalculado(new BigDecimal("8.000000"));
        order.setTotalCobrado(new BigDecimal("8.000000"));
        order.setOriginRequest(OriginRequest.TERMINAL);
        order.setStatus(OrderStatus.PROCESSED);
        order.setPaidAt(Instant.now());
        order = orders.saveAndFlush(order);
        orderId = order.getIdOrder();

        OrderItem item = new OrderItem();
        item.setEmpresa(empresa);
        item.setOrder(order);
        item.setProduto(produto);
        item.setCodigoInterno(produto.getCodigoInterno());
        item.setNome(produto.getNome());
        item.setUnidadeMedida(UnidadeMedida.UN);
        item.setQuantidade(BigDecimal.ONE);
        item.setPrecoOriginal(new BigDecimal("10.000000"));
        item.setPrecoUnitarioAplicado(new BigDecimal("8.000000"));
        item.setDescontoCalculado(new BigDecimal("2.000000"));
        item.setSubtotalCalculado(new BigDecimal("8.000000"));
        orderItems.saveAndFlush(item);

        LoyaltyAccount account = new LoyaltyAccount();
        account.setEmpresa(empresa);
        account.setUser(user);
        account.setPointsBalance(100);
        accounts.saveAndFlush(account);

        Reward reward = new Reward();
        reward.setEmpresa(empresa);
        reward.setCondominio(condominio);
        reward.setNome("Desconto teste");
        reward.setTipoDesconto(RewardDiscountType.VALOR_FIXO);
        reward.setValorDesconto(new BigDecimal("1.000000"));
        reward.setCustoPontos(10);
        reward.setValorMinimoCompra(BigDecimal.ZERO);
        reward.setValidade(Instant.now().plusSeconds(3600));
        reward.setValidadeDias(30);
        rewardId = rewards.saveAndFlush(reward).getId();

        CustomerCoupon coupon = new CustomerCoupon();
        coupon.setEmpresa(empresa);
        coupon.setUser(user);
        coupon.setNome("Cupom existente");
        coupon.setTipo(RewardDiscountType.VALOR_FIXO);
        coupon.setValor(new BigDecimal("1.000000"));
        coupon.setValorMinimoCompra(BigDecimal.ZERO);
        coupon.setValidade(Instant.now().plusSeconds(3600));
        couponId = coupons.saveAndFlush(coupon).getId();
    }

    @TestFactory
    Collection<DynamicTest> quinzeRotasPassamPorServiceERepositoriesReais() {
        List<DynamicTest> tests = new ArrayList<>();
        int number = 0;
        for (MockHttpServletRequestBuilder request : originalFifteenRequests()) {
            int current = ++number;
            tests.add(DynamicTest.dynamicTest("rota real " + current,
                    () -> mvc.perform(request.header(HttpHeaders.AUTHORIZATION, authorization))
                            .andExpect(status().is2xxSuccessful())));
        }
        return tests;
    }

    @TestFactory
    Collection<DynamicTest> quinzeRotasRejeitamAusenciaDeJwt() {
        List<DynamicTest> tests = new ArrayList<>();
        int number = 0;
        for (MockHttpServletRequestBuilder request : originalFifteenRequests()) {
            int current = ++number;
            tests.add(DynamicTest.dynamicTest("rota anônima " + current,
                    () -> mvc.perform(request).andExpect(status().isUnauthorized())));
        }
        return tests;
    }

    @Test
    void catalogoAplicaPromocaoENaoVazaProdutosInativosOuDeOutroTenant() throws Exception {
        Produto inactive = produto(empresa, "INACTIVE-" + java.util.UUID.randomUUID(), "Inativo", false);
        estoque(condominio, inactive, true);
        Produto inactiveStockProduct = produto(empresa, "INACTIVE-STOCK-" + java.util.UUID.randomUUID(),
                "Estoque inativo", true);
        estoque(condominio, inactiveStockProduct, false);
        Condominio otherCondo = condominio(empresa, "Outro condomínio");
        Produto otherCondoProduct = produto(empresa, "OTHER-CONDO-" + java.util.UUID.randomUUID(), "Outro condomínio", true);
        estoque(otherCondo, otherCondoProduct, true);
        Empresa otherCompany = empresa("Outra empresa", "20" + digits(java.util.UUID.randomUUID().toString(), 12),
                java.util.UUID.randomUUID() + "@empresa.test");
        Condominio foreignCondo = condominio(otherCompany, "Condomínio externo");
        Produto foreign = produto(otherCompany, "FOREIGN-" + java.util.UUID.randomUUID(), "Externo", true);
        estoque(foreignCondo, foreign, true);

        mvc.perform(get("/app-cliente/catalogo").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].nome").value("Água"))
                .andExpect(jsonPath("$.content[0].precoFinal").value(8.0))
                .andExpect(jsonPath("$.content[0].promocao.nome").value("Oferta"));
    }

    @Test
    void detalheDeOutraContaRetorna404Opaco() throws Exception {
        User other = user(empresa, condominio, nextCpf(), java.util.UUID.randomUUID() + "@cliente.test", RoleUser.USER);
        Order ownOrder = orders.findById(orderId).orElseThrow();
        ownOrder.setUser(other);
        orders.saveAndFlush(ownOrder);

        mvc.perform(get("/app-cliente/compras/{id}", orderId).header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PURCHASE_NOT_FOUND"));
    }

    @Test
    void dadosDoComprovanteDevolvemCompraAprovadaDoProprioCliente() throws Exception {
        Endereco address = new Endereco();
        address.setEmpresa(empresa);
        address.setRua("Rua das Flores");
        address.setNumero("123");
        address.setBairro("Centro");
        address.setCidade("Feira de Santana");
        address.setEstado("BA");
        address.setCep("44000-000");
        condominio.setEndereco(enderecos.saveAndFlush(address));
        condominios.saveAndFlush(condominio);
        empresa.setLogoUrl("https://cdn.example.test/logo.png");
        empresas.saveAndFlush(empresa);

        PaymentAttempt payment = new PaymentAttempt();
        payment.setOrder(orders.findById(orderId).orElseThrow());
        payment.setEmpresa(empresa);
        payment.setAttemptNumber(1);
        payment.setTipo(PagamentoTipo.CREDIT_CARD);
        payment.setStatus(PagamentoStatus.PROCESSED);
        payment.setValor(new BigDecimal("8.000000"));
        payment.setPaidAt(Instant.parse("2026-09-12T18:40:00Z"));
        payment.setTransactionId("mp-777");
        pagamentos.saveAndFlush(payment);

        mvc.perform(get("/app-cliente/compras/{id}/comprovante/dados", orderId)
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.estabelecimento.logoUrl").value("https://cdn.example.test/logo.png"))
                .andExpect(jsonPath("$.numeroCompra").value(orderId))
                .andExpect(jsonPath("$.hora").value("15:40"))
                .andExpect(jsonPath("$.itens[0].descricao").value("Água"))
                .andExpect(jsonPath("$.itens[0].valorUnitario").value(10.0))
                .andExpect(jsonPath("$.itens[0].valorFinal").value(8.0))
                .andExpect(jsonPath("$.descontos[0].tipo").value("PROMOCAO"))
                .andExpect(jsonPath("$.total").value(8.0))
                .andExpect(jsonPath("$.pagamento.transacaoId").value("mp-777"))
                .andExpect(jsonPath("$.cliente.vinculado").value(true))
                .andExpect(jsonPath("$.cliente.primeiroNome").value("Cliente"));
    }

    @Test
    void dadosDoComprovanteDeOutraContaRetornam404Opaco() throws Exception {
        User other = user(empresa, condominio, nextCpf(), java.util.UUID.randomUUID() + "@cliente.test", RoleUser.USER);
        Order ownOrder = orders.findById(orderId).orElseThrow();
        ownOrder.setUser(other);
        orders.saveAndFlush(ownOrder);

        mvc.perform(get("/app-cliente/compras/{id}/comprovante/dados", orderId)
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PURCHASE_NOT_FOUND"));
    }

    @Test
    void bootstrapNaoAceitaEmpresaOuCondominioArbitrarios() throws Exception {
        mvc.perform(get("/app-cliente/bootstrap")
                        .queryParam("empresaId", "outra")
                        .queryParam("condominioId", "outro")
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.condominio.id").value(condominio.getIdCondominio()));
    }

    @Test
    void usuarioSemVinculoRecebeErroExplicito() throws Exception {
        User global = user(null, null, nextCpf(), java.util.UUID.randomUUID() + "@cliente.test", RoleUser.USER);
        String globalToken = "Bearer " + tokens.generateToken(global);
        mvc.perform(get("/app-cliente/bootstrap").header(HttpHeaders.AUTHORIZATION, globalToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TENANT_LINK_REQUIRED"));
    }

    @Test
    void appClienteServicePrincipalEhBeanReal() {
        assertThat(appClienteService.getClass().getName()).doesNotContain("MockitoMock");
    }

    @Test
    void identificationQrIsOpaqueHasTtlAndOnlyHashIsPersisted() throws Exception {
        String json=mvc.perform(post("/app-cliente/identificacao-tokens")
                        .header(HttpHeaders.AUTHORIZATION,authorization))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.qrPayload").value(org.hamcrest.Matchers.startsWith("app247://customer-link/")))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        var node=objectMapper.readTree(json);
        String payload=node.get("qrPayload").asText();
        String opaque=payload.substring("app247://customer-link/".length());
        assertThat(opaque).hasSize(43).doesNotContain("@", ".");
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(opaque.getBytes(StandardCharsets.US_ASCII)));
        var persisted=identificationTokens.findAll().stream()
                .filter(value->hash.equals(value.getTokenHash())).findFirst().orElseThrow();
        assertThat(persisted.getTokenHash()).hasSize(64).isNotEqualTo(opaque);
        assertThat(persisted.getExpiresAt()).isAfter(Instant.now().plusSeconds(90));
        assertThat(persisted.getExpiresAt()).isBefore(Instant.now().plusSeconds(130));
        assertThat(json).doesNotContain("userId","cpf","empresaId","condominioId","JWT");
    }

    private List<MockHttpServletRequestBuilder> originalFifteenRequests() {
        return List.of(
                get("/app-cliente/bootstrap"), get("/app-cliente/catalogo"), get("/app-cliente/compras"),
                get("/app-cliente/compras/{id}", orderId), get("/app-cliente/compras/{id}/comprovante", orderId),
                post("/app-cliente/compras/{id}/comprovante/envios", orderId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"canal\":\"EMAIL\",\"destinatario\":\"cliente@example.test\"}"),
                post("/app-cliente/identificacao-tokens"), get("/app-cliente/fidelidade"),
                get("/app-cliente/recompensas"),
                post("/app-cliente/recompensas/{id}/resgates", rewardId)
                        .header("Idempotency-Key", "route-" + java.util.UUID.randomUUID()),
                get("/app-cliente/cupons"), post("/app-cliente/cupons/{id}/tokens", couponId),
                get("/app-cliente/preferencias"),
                put("/app-cliente/preferencias").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"autoEmail\":true,\"autoWhatsapp\":false}"),
                post("/app-cliente/compras/{id}/suporte", orderId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoria\":\"PAYMENT_PROBLEM\",\"mensagem\":\"Ajuda\"}"));
    }

    private Empresa empresa(String nome, String cnpj, String email) {
        return empresas.saveAndFlush(Empresa.builder().razaoSocial(nome).nomeFantasia(nome).cnpj(cnpj)
                .email(email).ativo(true).build());
    }

    private Condominio condominio(Empresa owner, String nome) {
        Condominio value = new Condominio(); value.setEmpresa(owner); value.setNome(nome); value.setAtivo(true);
        return condominios.saveAndFlush(value);
    }

    private User user(Empresa owner, Condominio condo, String cpf, String email, RoleUser role) {
        return users.saveAndFlush(User.builder().empresa(owner).condominio(condo).nome("Cliente").email(email)
                .senha("{noop}senha").cpf(cpf).ativo(true).emailVerificado(false).role(role).build());
    }

    private Produto produto(Empresa owner, String sku, String nome, boolean active) {
        Produto value = new Produto(); value.setEmpresa(owner); value.setCodigoInterno(sku); value.setNome(nome);
        value.setPrecoVenda(new BigDecimal("10.000000")); value.setCategoria(ProdutoCategoria.BEBIDA);
        value.setUnidadeMedida(UnidadeMedida.UN); value.setAtivo(active); return produtos.saveAndFlush(value);
    }

    private void estoque(Condominio condo, Produto product, boolean active) {
        EstoqueCondominio stock = new EstoqueCondominio(); stock.setEmpresa(condo.getEmpresa());
        stock.setCondominio(condo); stock.setProduto(product); stock.setQuantidade(BigDecimal.TEN);
        stock.setAtivo(active); estoques.saveAndFlush(stock);
    }

    private void promocao(Condominio condo, Produto product) {
        Promocao promotion = new Promocao(); promotion.setEmpresa(condo.getEmpresa()); promotion.setCondominio(condo);
        promotion.setAbrangencia(AbrangenciaPromocao.CONDOMINIO); promotion.setNome("Oferta");
        promotion.setTipo(TipoPromocao.DESCONTO_FIXO); promotion.setValor(new BigDecimal("2.000000"));
        promotion.setInicio(Instant.now().minusSeconds(60)); promotion.setFim(Instant.now().plusSeconds(3600));
        promotion.setAtivo(true); promotion.adicionarProduto(product); promocoes.saveAndFlush(promotion);
    }

    private static String digits(String source, int length) {
        String value = source.replaceAll("\\D", "") + "12345678901234567890";
        return value.substring(0, length);
    }

    private static String nextCpf() {
        return Long.toString(CPF_SEQUENCE.getAndIncrement());
    }
}
