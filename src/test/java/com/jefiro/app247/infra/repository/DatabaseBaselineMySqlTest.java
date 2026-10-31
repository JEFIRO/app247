package com.jefiro.app247.infra.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import com.jefiro.app247.domain.model.dto.OrderResponse;
import com.jefiro.app247.infra.service.CouponLifecycleService;
import com.jefiro.app247.infra.service.MercadoPagoCobrancaService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Valida a fonte real do schema: MySQL vazio -> Flyway -> Hibernate validate.
 * A classe é ignorada automaticamente quando Docker não está disponível.
 */
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "payment.reconciliation.initial-delay-ms=3600000",
        "terminal.telemetry.offline-check-delay-ms=3600000"
})
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureMockMvc
class DatabaseBaselineMySqlTest {
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.4")
            .withDatabaseName("app247_test")
            .withUsername("app247")
            .withPassword("app247");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.MySQLDialect");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired OrderRepository orders;
    @Autowired CouponLifecycleService couponLifecycle;
    @MockitoBean MercadoPagoCobrancaService mercadoPago;

    @Test
    void baselineVaziaExecutaVinteETresMigrationsECriaQuarentaENoveTabelas() {
        Integer migrations = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success = 1", Integer.class);
        Integer tables = jdbc.queryForObject("""
                select count(*) from information_schema.tables
                 where table_schema = database()
                   and table_type = 'BASE TABLE'
                   and table_name <> 'flyway_schema_history'
                """, Integer.class);

        assertThat(migrations).isEqualTo(23);
        assertThat(tables).isEqualTo(49);
    }

    @Test
    @Transactional
    void p1bImpedeConfiguracaoDuplicadaValoresInvalidosEReferenciasCrossTenant() {
        insertEmpresa(EMPRESA_A, "A");
        insertEmpresa(EMPRESA_B, "B");
        insertCondominio(CONDOMINIO_A, EMPRESA_A);
        insertCondominio(CONDOMINIO_B, EMPRESA_B);
        insertUser(USER_A, EMPRESA_A, CONDOMINIO_A, "11111111111", "admin-a@example.test");
        insertUser(USER_B, EMPRESA_B, CONDOMINIO_B, "22222222222", "admin-b@example.test");

        jdbc.update("""
                insert into app_cliente_config
                (empresa_id,catalogo,promocoes,fidelidade,pontos,cashback,recompensas,cupons,
                 qr_identificacao,preferencias,suporte,loyalty_mode,points_per_currency,
                 cashback_percent,created_at,updated_at)
                values (?,true,true,true,true,false,true,true,true,true,true,'POINTS',1.000000,null,
                        utc_timestamp(6),utc_timestamp(6))
                """, EMPRESA_A);
        assertThatThrownBy(() -> jdbc.update("""
                insert into app_cliente_config
                (empresa_id,catalogo,promocoes,fidelidade,pontos,cashback,recompensas,cupons,
                 qr_identificacao,preferencias,suporte,loyalty_mode,points_per_currency,
                 cashback_percent,created_at,updated_at)
                values (?,false,false,false,false,false,false,false,false,false,false,'NONE',null,null,
                        utc_timestamp(6),utc_timestamp(6))
                """, EMPRESA_A)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("""
                insert into app_cliente_config
                (empresa_id,catalogo,promocoes,fidelidade,pontos,cashback,recompensas,cupons,
                 qr_identificacao,preferencias,suporte,loyalty_mode,points_per_currency,
                 cashback_percent,created_at,updated_at)
                values (?,true,true,true,false,true,false,false,true,true,true,'CASHBACK',null,150.000000,
                        utc_timestamp(6),utc_timestamp(6))
                """, EMPRESA_B)).isInstanceOf(DataAccessException.class);

        String reward = id(81);
        jdbc.update("""
                insert into reward
                (id,empresa_id,condominio_id,nome,tipo_desconto,valor_desconto,custo_pontos,
                 valor_minimo_compra,desconto_maximo,validade,validade_dias,limite_uso_por_usuario,
                 ativo,created_at,updated_at)
                values (?,?,?,'10% OFF','PERCENTUAL',10.000000,500,100.000000,25.000000,
                        date_add(utc_timestamp(6),interval 30 day),30,1,true,utc_timestamp(6),utc_timestamp(6))
                """, reward, EMPRESA_A, CONDOMINIO_A);
        assertThatThrownBy(() -> jdbc.update("""
                insert into reward
                (id,empresa_id,condominio_id,nome,tipo_desconto,valor_desconto,custo_pontos,
                 valor_minimo_compra,validade,validade_dias,limite_uso_por_usuario,ativo,created_at,updated_at)
                values (?,?,?,'Cross tenant','VALOR_FIXO',10.000000,100,0.000000,
                        date_add(utc_timestamp(6),interval 30 day),30,1,true,utc_timestamp(6),utc_timestamp(6))
                """, id(82), EMPRESA_A, CONDOMINIO_B)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("""
                insert into reward
                (id,empresa_id,nome,tipo_desconto,valor_desconto,custo_pontos,valor_minimo_compra,
                 validade,validade_dias,limite_uso_por_usuario,ativo,created_at,updated_at)
                values (?,?,'Percentual inválido','PERCENTUAL',150.000000,100,0.000000,
                        date_add(utc_timestamp(6),interval 30 day),30,1,true,utc_timestamp(6),utc_timestamp(6))
                """, id(83), EMPRESA_A)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("""
                insert into customer_coupon
                (id,empresa_id,user_id,reward_id,nome,tipo,valor,valor_minimo_compra,
                 desconto_maximo,validade,status,created_at,updated_at)
                values (?,?,?,?,'Cupom cross tenant','PERCENTUAL',10.000000,100.000000,25.000000,
                        date_add(utc_timestamp(6),interval 30 day),'AVAILABLE',utc_timestamp(6),utc_timestamp(6))
                """, id(84), EMPRESA_A, USER_B, reward)).isInstanceOf(DataAccessException.class);

        jdbc.update("""
                insert into loyalty_account
                (id,empresa_id,user_id,points_balance,cashback_balance,lock_version,created_at,updated_at)
                values (?,?,?,1000,0.000000,0,utc_timestamp(6),utc_timestamp(6))
                """, id(85), EMPRESA_A, USER_A);
        assertThatThrownBy(() -> jdbc.update("""
                insert into loyalty_movement
                (id,empresa_id,account_id,type,points_delta,description,idempotency_key,created_at)
                values (?,?,?,'PURCHASE',10,'Cross tenant','movement-cross-tenant',utc_timestamp(6))
                """, id(86), EMPRESA_B, id(85))).isInstanceOf(DataAccessException.class);
    }

    @Test
    @Transactional
    void conviteDeVinculoRejeitaCondominioCrossTenantETokenDuplicado() {
        insertEmpresa(EMPRESA_A, "A");
        insertEmpresa(EMPRESA_B, "B");
        insertCondominio(CONDOMINIO_A, EMPRESA_A);
        insertCondominio(CONDOMINIO_B, EMPRESA_B);
        insertUser(USER_A, EMPRESA_A, CONDOMINIO_A, "11111111111", "admin-a@example.test");

        assertThatThrownBy(() -> insertTenantInvitation(
                id(71), EMPRESA_A, CONDOMINIO_B, USER_A, "cpf-hash-a", "token-hash-a"))
                .isInstanceOf(DataIntegrityViolationException.class);

        insertTenantInvitation(id(72), EMPRESA_A, CONDOMINIO_A, USER_A,
                "cpf-hash-a", "token-hash-a");
        assertThatThrownBy(() -> insertTenantInvitation(
                id(73), EMPRESA_A, CONDOMINIO_A, USER_A, "cpf-hash-b", "token-hash-a"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void leaseMpImpedeVinculoAtivoDuploEMasPermiteReusoAposUnlink() {
        insertEmpresa(EMPRESA_A, "A");
        insertEmpresa(EMPRESA_B, "B");
        String bindingA = id(61);
        String bindingB = id(62);
        String mpUserId = "mp-user-compartilhado";
        insertMercadoPagoBinding(bindingA, EMPRESA_A, mpUserId, "token-a");
        insertMercadoPagoBinding(bindingB, EMPRESA_B, mpUserId, "token-b");
        jdbc.update("""
                insert into mercado_pago_conta_ativa
                (mp_user_id,empresa_id,mercado_pago_conta_id,created_at)
                values (?,?,?,utc_timestamp(6))
                """, mpUserId, EMPRESA_A, bindingA);

        assertThatThrownBy(() -> jdbc.update("""
                insert into mercado_pago_conta_ativa
                (mp_user_id,empresa_id,mercado_pago_conta_id,created_at)
                values (?,?,?,utc_timestamp(6))
                """, mpUserId, EMPRESA_B, bindingB))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update("delete from mercado_pago_conta_ativa where mp_user_id=?", mpUserId);
        jdbc.update("""
                update mercado_pago_conta
                   set status='UNLINKED', access_token=null, refresh_token=null,
                       unlinked_at=utc_timestamp(6), unlink_reason='USER_UNLINK'
                 where id=?
                """, bindingA);
        jdbc.update("""
                insert into mercado_pago_conta_ativa
                (mp_user_id,empresa_id,mercado_pago_conta_id,created_at)
                values (?,?,?,utc_timestamp(6))
                """, mpUserId, EMPRESA_B, bindingB);

        assertThat(jdbc.queryForObject(
                "select empresa_id from mercado_pago_conta_ativa where mp_user_id=?",
                String.class, mpUserId)).isEqualTo(EMPRESA_B);
        assertThat(jdbc.queryForObject(
                "select count(*) from mercado_pago_conta where mp_user_id=?",
                Integer.class, mpUserId)).isEqualTo(2);
    }

    @Test
    @Transactional
    void skuEBarcodeSaoUnicosPorEmpresaENaoGlobalmente() {
        insertEmpresa(EMPRESA_A, "A");
        insertEmpresa(EMPRESA_B, "B");
        insertProduto(PRODUTO_A, EMPRESA_A, "001", "Produto A");
        insertProduto(PRODUTO_B, EMPRESA_B, "001", "Produto B");
        insertBarcode(BARCODE_A, EMPRESA_A, PRODUTO_A, "789123");
        insertBarcode(BARCODE_B, EMPRESA_B, PRODUTO_B, "789123");

        assertThat(jdbc.queryForObject(
                "select count(*) from produto_codigo_barras where codigo_barras='789123'", Integer.class))
                .isEqualTo(2);
        assertThatThrownBy(() -> insertProduto(id(21), EMPRESA_A, "001", "SKU duplicado"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertBarcode(id(22), EMPRESA_A, PRODUTO_A, "789123"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertBarcode(id(23), EMPRESA_A, PRODUTO_A, "789124"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void bancoBloqueiaEstoqueCrossTenantEMantemSaldoNegativoFracionado() {
        insertEmpresa(EMPRESA_A, "A");
        insertEmpresa(EMPRESA_B, "B");
        insertCondominio(CONDOMINIO_A, EMPRESA_A);
        insertProduto(PRODUTO_A, EMPRESA_A, "A-001", "Produto A");
        insertProduto(PRODUTO_B, EMPRESA_B, "B-001", "Produto B");

        assertThatThrownBy(() -> insertEstoque(id(31), EMPRESA_A, CONDOMINIO_A, PRODUTO_B, "0.100"))
                .isInstanceOf(DataIntegrityViolationException.class);

        insertEstoque(id(32), EMPRESA_A, CONDOMINIO_A, PRODUTO_A, "-0.250");
        BigDecimal saldo = jdbc.queryForObject(
                "select quantidade from estoque_condominio where id=?", BigDecimal.class, id(32));
        assertThat(saldo).isEqualByComparingTo("-0.250");

        assertThatThrownBy(() -> jdbc.update("""
                insert into estoque_empresa
                (id,empresa_id,produto_id,quantidade,ativo,lock_version,created_at,updated_at)
                values (?,?,?,1.000,true,0,utc_timestamp(6),utc_timestamp(6))
                """, id(33), EMPRESA_A, PRODUTO_B))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void orderItemEhSnapshotFracionadoEOrderAceitaVariasTentativas() {
        prepararCheckout();
        jdbc.update("""
                insert into cart_item
                (id,empresa_id,carrinho_id,produto_id,nome,unidade_medida,quantidade,
                 preco_original,preco_unitario_aplicado,desconto_calculado,subtotal_calculado,
                 requer_peso,status,created_at,updated_at)
                values (?,?,?,?,?,'KG',0.350,10.990000,10.165750,0.824250,3.558013,false,'VALIDATED',utc_timestamp(6),utc_timestamp(6))
                """, CART_ITEM, EMPRESA_A, CARRINHO, PRODUTO_A, "Produto original");
        jdbc.update("""
                insert into order_item
                (id,empresa_id,order_id,produto_id,codigo_interno,nome,unidade_medida,quantidade,
                 preco_original,preco_unitario_aplicado,desconto_calculado,subtotal_calculado,created_at)
                values (?,?,?,?,?,?,'KG',0.350,10.990000,10.165750,0.824250,3.558013,utc_timestamp(6))
                """, ORDER_ITEM, EMPRESA_A, ORDER, PRODUTO_A, "A-001", "Produto original");

        insertAttempt(ATTEMPT_1, 1, "CANCELED", "idem-1", "ref-1", "mp-order-1", "mp-payment-1");
        insertAttempt(ATTEMPT_2, 2, "PROCESSED", "idem-2", "ref-2", "mp-order-2", "mp-payment-2");
        jdbc.update("delete from cart_item where id=?", CART_ITEM);
        jdbc.update("update produto set nome='Produto alterado' where id=?", PRODUTO_A);

        assertThat(jdbc.queryForObject("select quantidade from order_item where id=?", BigDecimal.class, ORDER_ITEM))
                .isEqualByComparingTo("0.350");
        assertThat(jdbc.queryForObject("select nome from order_item where id=?", String.class, ORDER_ITEM))
                .isEqualTo("Produto original");
        assertThat(jdbc.queryForObject("select count(*) from payment_attempt where order_id=?", Integer.class, ORDER))
                .isEqualTo(2);
        assertThatThrownBy(() -> insertAttempt(id(51), 3, "PENDING", "idem-1", "ref-3", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertAttempt(id(52), 3, "PENDING", "idem-3", "ref-3", "mp-order-1", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertAttempt(id(53), 3, "PENDING", "idem-4", "ref-4", null, "mp-payment-2"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertAttempt(id(54), 3, "PENDING", "idem-5", "ref-1", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void p1cImpedeDuasTentativasAtivasEProtegeSnapshotFinanceiro() {
        prepararCheckout();
        insertAttempt(ATTEMPT_1, 1, "UNKNOWN", "idem-1", "ref-1", null, null);

        assertThatThrownBy(() -> insertAttempt(
                ATTEMPT_2, 2, "PENDING", "idem-2", "ref-2", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbc.update("""
                update orders
                   set desconto_cupom=1.000000
                 where id=?
                """, ORDER)).isInstanceOf(DataAccessException.class);
    }

    @Test
    void requestsConcorrentesDoTerminalCriamUmaTentativaEUmaChamadaRemota() throws Exception {
        PaymentFixture fixture = paymentFixture("A");
        reset(mercadoPago);
        when(mercadoPago.createRemoteOrder(anyString())).thenAnswer(call -> {
            String orderId = call.getArgument(0);
            Thread.sleep(120);
            return new OrderResponse(
                    "mp-" + orderId, "point", "mp-user-" + fixture.suffix,
                    orderId + "-1", null, null, null, null, null,
                    "created", "created", null, "2026-09-27T12:00:00Z",
                    1, null, new OrderResponse.Transactions(List.of()));
        });

        var pool = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Integer> request = () -> mvc.perform(
                            post("/pagamento/terminal/{cartId}", fixture.cartId)
                                    .header("X-Terminal-Token", fixture.deviceCredential)
                                    .header("X-Terminal-Id", fixture.terminalId))
                    .andReturn().getResponse().getStatus();
            var results = pool.invokeAll(List.of(request, request));
            assertThat(results.get(0).get(10, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(results.get(1).get(10, TimeUnit.SECONDS)).isEqualTo(200);
        } finally {
            pool.shutdownNow();
        }

        assertThat(jdbc.queryForObject(
                "select count(*) from payment_attempt where order_id=?", Integer.class,
                fixture.orderId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from payment_attempt where active_order_id=?", Integer.class,
                fixture.orderId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select amount_requested from payment_attempt where order_id=?", BigDecimal.class,
                fixture.orderId)).isEqualByComparingTo("108.000000");
        assertThat(jdbc.queryForObject(
                "select price_finalized_at is not null from orders where id=?", Boolean.class,
                fixture.orderId)).isTrue();
        verify(mercadoPago, times(1)).createRemoteOrder(fixture.orderId);
    }

    @Test
    void terminalDeOutroTenantNaoPodeIniciarPagamentoDaOrder() throws Exception {
        PaymentFixture fixtureA = paymentFixture("A");
        PaymentFixture fixtureB = paymentFixture("B");
        reset(mercadoPago);

        mvc.perform(post("/pagamento/terminal/{cartId}", fixtureA.cartId)
                        .header("X-Terminal-Token", fixtureA.deviceCredential)
                        .header("X-Terminal-Id", fixtureB.terminalId))
                .andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject(
                "select count(*) from payment_attempt where order_id=?", Integer.class,
                fixtureA.orderId)).isZero();
        verify(mercadoPago, never()).createRemoteOrder(anyString());
    }

    @Test
    void endpointDePagamentoRecusaCredencialAusente() throws Exception {
        mvc.perform(post("/pagamento/terminal/{cartId}", UUID.randomUUID())
                        .header("X-Terminal-Id", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cicloRealDoCupomConsomeNaAprovacaoELiberaSomenteEmFimNaoAprovado() {
        PaymentFixture approved = paymentFixture("COUPON-USED");
        String approvedUser = UUID.randomUUID().toString();
        String approvedCoupon = UUID.randomUUID().toString();
        insertUser(approvedUser, approved.empresaId, approved.condominioId,
                randomCpf(), approved.suffix + "-used@example.test");
        insertReservedCoupon(approvedCoupon, approved.empresaId, approvedUser,
                approved.orderId);
        linkCouponSnapshot(approved.orderId, approvedUser, approvedCoupon);

        couponLifecycle.use(orders.findById(approved.orderId).orElseThrow());
        couponLifecycle.use(orders.findById(approved.orderId).orElseThrow());

        assertThat(jdbc.queryForObject(
                "select status from customer_coupon where id=?", String.class,
                approvedCoupon)).isEqualTo("USED");
        assertThat(jdbc.queryForObject(
                "select reserved_order_id from customer_coupon where id=?", String.class,
                approvedCoupon)).isEqualTo(approved.orderId);

        PaymentFixture failed = paymentFixture("COUPON-RELEASE");
        String failedUser = UUID.randomUUID().toString();
        String failedCoupon = UUID.randomUUID().toString();
        insertUser(failedUser, failed.empresaId, failed.condominioId,
                randomCpf(), failed.suffix + "-release@example.test");
        insertReservedCoupon(failedCoupon, failed.empresaId, failedUser, failed.orderId);
        linkCouponSnapshot(failed.orderId, failedUser, failedCoupon);

        couponLifecycle.release(orders.findById(failed.orderId).orElseThrow());

        assertThat(jdbc.queryForObject(
                "select status from customer_coupon where id=?", String.class,
                failedCoupon)).isEqualTo("AVAILABLE");
        assertThat(jdbc.queryForObject(
                "select reserved_order_id is null from customer_coupon where id=?",
                Boolean.class, failedCoupon)).isTrue();
    }

    private void prepararCheckout() {
        insertEmpresa(EMPRESA_A, "A");
        insertCondominio(CONDOMINIO_A, EMPRESA_A);
        insertProduto(PRODUTO_A, EMPRESA_A, "A-001", "Produto original");
        jdbc.update("""
                insert into terminal
                (id,condominio_id,nome,codigo,ativo,status,created_at,updated_at)
                values (?,?,?,? ,true,'ONLINE',utc_timestamp(6),utc_timestamp(6))
                """, TERMINAL, CONDOMINIO_A, "Terminal", "T-001");
        jdbc.update("""
                insert into carrinho
                (id,empresa_id,condominio_id,terminal_id,status,subtotal,created_at,updated_at)
                values (?,?,?,?,'READY_FOR_PAYMENT',3.558013,utc_timestamp(6),utc_timestamp(6))
                """, CARRINHO, EMPRESA_A, CONDOMINIO_A, TERMINAL);
        jdbc.update("""
                insert into orders
                (id,empresa_id,condominio_id,terminal_id,carrinho_id,status,origin_request,
                 subtotal,subtotal_pos_promocoes,desconto_promocoes,desconto_cupom,
                 desconto,total_calculado,total_cobrado,price_finalized_at,version,created_at,updated_at)
                values (?,?,?, ?,?,'PENDING','TERMINAL',3.846500,3.558013,0.288487,0.000000,
                        0.288487,3.558013,3.560000,utc_timestamp(6),0,utc_timestamp(6),utc_timestamp(6))
                """, ORDER, EMPRESA_A, CONDOMINIO_A, TERMINAL, CARRINHO);
    }

    private PaymentFixture paymentFixture(String label) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String empresaId = UUID.randomUUID().toString();
        String condominioId = UUID.randomUUID().toString();
        String terminalId = UUID.randomUUID().toString();
        String productId = UUID.randomUUID().toString();
        String cartId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();
        String accountId = UUID.randomUUID().toString();
        String mpUser = "mp-user-" + suffix;
        String pointId = "point-" + suffix;
        String deviceCredential = "test-device-" + suffix;

        jdbc.update("""
                insert into empresa
                (id,tenant_id,razao_social,nome_fantasia,cnpj,email,ativo,created_at,updated_at)
                values (?,?,?,?,?,?,true,utc_timestamp(6),utc_timestamp(6))
                """, empresaId, empresaId, "Empresa " + label, "Empresa " + label,
                "cnpj-" + suffix, suffix + "@payment.test");
        insertCondominio(condominioId, empresaId);
        insertProduto(productId, empresaId, "SKU-" + suffix, "Produto " + suffix);
        jdbc.update("update produto set preco_venda=120.000000 where id=?", productId);
        jdbc.update("""
                insert into terminal
                (id,condominio_id,nome,codigo,mercado_pago_terminal_id,ativo,status,lifecycle_state,
                 device_credential_hash,device_credential_version,device_credential_created_at,
                 created_at,updated_at)
                values (?,?,?,?,?,true,'ONLINE','ACTIVE',?,1,utc_timestamp(6),utc_timestamp(6),utc_timestamp(6))
                """, terminalId, condominioId, "Terminal " + suffix,
                "TERM-" + suffix, pointId, sha256(deviceCredential));
        jdbc.update("""
                insert into carrinho
                (id,empresa_id,condominio_id,terminal_id,status,subtotal,created_at,updated_at)
                values (?,?,?,?,'READY_FOR_PAYMENT',108.000000,utc_timestamp(6),utc_timestamp(6))
                """, cartId, empresaId, condominioId, terminalId);
        jdbc.update("""
                insert into cart_item
                (id,empresa_id,carrinho_id,produto_id,nome,unidade_medida,quantidade,
                 preco_original,preco_unitario_aplicado,desconto_calculado,subtotal_calculado,
                 requer_peso,status,created_at,updated_at)
                values (?,?,?,?,?,'UN',1.000,120.000000,108.000000,12.000000,108.000000,
                        false,'VALIDATED',utc_timestamp(6),utc_timestamp(6))
                """, UUID.randomUUID().toString(), empresaId, cartId, productId,
                "Produto " + suffix);
        jdbc.update("""
                insert into orders
                (id,empresa_id,condominio_id,terminal_id,carrinho_id,status,origin_request,
                 subtotal,subtotal_pos_promocoes,desconto_promocoes,desconto_cupom,
                 desconto,total_calculado,total_cobrado,version,created_at,updated_at)
                values (?,?,?,?,?,'PENDING','TERMINAL',120.000000,108.000000,12.000000,0.000000,
                        12.000000,108.000000,108.000000,0,utc_timestamp(6),utc_timestamp(6))
                """, orderId, empresaId, condominioId, terminalId, cartId);
        jdbc.update("""
                insert into order_item
                (id,empresa_id,order_id,produto_id,codigo_interno,nome,unidade_medida,quantidade,
                 preco_original,preco_unitario_aplicado,desconto_calculado,subtotal_calculado,created_at)
                values (?,?,?,?,?,?,'UN',1.000,120.000000,108.000000,12.000000,108.000000,
                        utc_timestamp(6))
                """, UUID.randomUUID().toString(), empresaId, orderId, productId,
                "SKU-" + suffix, "Produto " + suffix);
        insertMercadoPagoBinding(accountId, empresaId, mpUser, "access-" + suffix);
        jdbc.update("""
                insert into mercado_pago_conta_ativa
                (mp_user_id,empresa_id,mercado_pago_conta_id,created_at)
                values (?,?,?,utc_timestamp(6))
                """, mpUser, empresaId, accountId);
        jdbc.update("""
                insert into terminal_point_binding
                (id,empresa_id,terminal_id,mercado_pago_conta_id,mercado_pago_terminal_id,
                 status,linked_at,created_at,updated_at)
                values (?,?,?,?,?,'ACTIVE',utc_timestamp(6),utc_timestamp(6),utc_timestamp(6))
                """, UUID.randomUUID().toString(), empresaId, terminalId, accountId, pointId);
        return new PaymentFixture(suffix, empresaId, condominioId, terminalId, cartId, orderId,
                deviceCredential);
    }

    private void insertReservedCoupon(String couponId, String empresaId, String userId,
                                      String orderId) {
        jdbc.update("""
                insert into customer_coupon
                (id,empresa_id,user_id,nome,tipo,valor,valor_minimo_compra,desconto_maximo,
                 validade,status,reserved_order_id,lock_version,created_at,updated_at)
                values (?,?,?,'R$ 8 OFF','VALOR_FIXO',8.000000,0.000000,null,
                        date_add(utc_timestamp(6),interval 30 day),'RESERVED',?,0,
                        utc_timestamp(6),utc_timestamp(6))
                """, couponId, empresaId, userId, orderId);
    }

    private void linkCouponSnapshot(String orderId, String userId, String couponId) {
        jdbc.update("""
                update orders
                   set user_id=?,coupon_id=?,desconto_cupom=8.000000,desconto=20.000000,
                       total_calculado=100.000000,total_cobrado=100.000000
                 where id=?
                """, userId, couponId, orderId);
    }

    private String randomCpf() {
        return String.format("%011d", Math.floorMod(
                UUID.randomUUID().getLeastSignificantBits(), 100_000_000_000L));
    }

    private record PaymentFixture(String suffix, String empresaId, String condominioId,
                                  String terminalId, String cartId, String orderId,
                                  String deviceCredential) {}

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private void insertAttempt(String attemptId, int number, String status, String idempotency,
                               String externalReference, String providerOrderId, String providerPaymentId) {
        jdbc.update("""
                insert into payment_attempt
                (id,empresa_id,order_id,attempt_number,provider,channel,source,status,amount_requested,
                 idempotency_key,external_reference,provider_order_id,provider_payment_id,created_at,updated_at)
                values (?,?,?,?,'MERCADO_PAGO','POINT','TERMINAL',?,3.560000,?,?,?,?,utc_timestamp(6),utc_timestamp(6))
                """, attemptId, EMPRESA_A, ORDER, number, status, idempotency, externalReference,
                providerOrderId, providerPaymentId);
    }

    private void insertEmpresa(String empresaId, String suffix) {
        jdbc.update("""
                insert into empresa
                (id,tenant_id,razao_social,nome_fantasia,cnpj,email,ativo,created_at,updated_at)
                values (?,?,?,?,?,?,true,utc_timestamp(6),utc_timestamp(6))
                """, empresaId, "tenant-" + suffix, "Empresa " + suffix, "Empresa " + suffix,
                "0000000000000" + suffix, "empresa-" + suffix + "@example.test");
    }

    private void insertCondominio(String condominioId, String empresaId) {
        jdbc.update("""
                insert into condominio (id,empresa_id,nome,ativo,created_at,updated_at)
                values (?,?,?,true,utc_timestamp(6),utc_timestamp(6))
                """, condominioId, empresaId, "Condomínio");
    }

    private void insertProduto(String produtoId, String empresaId, String sku, String nome) {
        jdbc.update("""
                insert into produto
                (id,empresa_id,codigo_interno,nome,preco_venda,categoria,unidade_medida,ativo,created_at,updated_at)
                values (?,?,?,?,10.990000,'OUTROS','UN',true,utc_timestamp(6),utc_timestamp(6))
                """, produtoId, empresaId, sku, nome);
    }

    private void insertBarcode(String barcodeId, String empresaId, String produtoId, String codigo) {
        jdbc.update("""
                insert into produto_codigo_barras
                (id,empresa_id,produto_id,codigo_barras,tipo,principal,ativo,created_at,updated_at)
                values (?,?,?,?,'EAN',true,true,utc_timestamp(6),utc_timestamp(6))
                """, barcodeId, empresaId, produtoId, codigo);
    }

    private void insertEstoque(String estoqueId, String empresaId, String condominioId,
                               String produtoId, String quantidade) {
        jdbc.update("""
                insert into estoque_condominio
                (id,empresa_id,condominio_id,produto_id,quantidade,ativo,lock_version,created_at,updated_at)
                values (?,?,?,?,?,true,0,utc_timestamp(6),utc_timestamp(6))
                """, estoqueId, empresaId, condominioId, produtoId, new BigDecimal(quantidade));
    }

    private void insertMercadoPagoBinding(String bindingId, String empresaId,
                                           String mpUserId, String accessToken) {
        jdbc.update("""
                insert into mercado_pago_conta
                (id,empresa_id,access_token,refresh_token,mp_user_id,status,linked_at,
                 token_created_at,token_expires_at,created_at,updated_at)
                values (?,?,?,?,?,'ACTIVE',utc_timestamp(6),utc_timestamp(6),
                        date_add(utc_timestamp(6), interval 6 hour),utc_timestamp(6),utc_timestamp(6))
                """, bindingId, empresaId, accessToken, "refresh-" + accessToken, mpUserId);
    }

    private void insertUser(String userId, String empresaId, String condominioId,
                            String cpf, String email) {
        jdbc.update("""
                insert into users
                (id,empresa_id,condominio_id,nome,email,senha,cpf,ativo,email_verificado,role,created_at,updated_at)
                values (?,?,?,'Administrador',?,'{noop}senha',?,true,false,'ADMIN',utc_timestamp(6),utc_timestamp(6))
                """, userId, empresaId, condominioId, email, cpf);
    }

    private void insertTenantInvitation(String invitationId, String empresaId,
                                        String condominioId, String createdByUserId,
                                        String cpfHash, String tokenHash) {
        jdbc.update("""
                insert into tenant_link_invitation
                (id,empresa_id,condominio_id,cpf_hash,token_hash,created_by_user_id,
                 expires_at,created_at)
                values (?,?,?,?,?,?,date_add(utc_timestamp(6), interval 1 hour),utc_timestamp(6))
                """, invitationId, empresaId, condominioId, cpfHash, tokenHash, createdByUserId);
    }

    private static String id(int value) {
        return "00000000-0000-0000-0000-" + String.format("%012d", value);
    }

    private static final String EMPRESA_A = id(1);
    private static final String EMPRESA_B = id(2);
    private static final String CONDOMINIO_A = id(3);
    private static final String TERMINAL = id(4);
    private static final String PRODUTO_A = id(5);
    private static final String PRODUTO_B = id(6);
    private static final String BARCODE_A = id(7);
    private static final String BARCODE_B = id(8);
    private static final String CARRINHO = id(9);
    private static final String CART_ITEM = id(10);
    private static final String ORDER = id(11);
    private static final String ORDER_ITEM = id(12);
    private static final String ATTEMPT_1 = id(13);
    private static final String ATTEMPT_2 = id(14);
    private static final String CONDOMINIO_B = id(15);
    private static final String USER_A = id(16);
    private static final String USER_B = id(17);
}
