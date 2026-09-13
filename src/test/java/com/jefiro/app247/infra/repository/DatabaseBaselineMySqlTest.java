package com.jefiro.app247.infra.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void baselineVaziaExecutaTrezeMigrationsECriaTrintaEOitoTabelas() {
        Integer migrations = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success = 1", Integer.class);
        Integer tables = jdbc.queryForObject("""
                select count(*) from information_schema.tables
                 where table_schema = database()
                   and table_type = 'BASE TABLE'
                   and table_name <> 'flyway_schema_history'
                """, Integer.class);

        assertThat(migrations).isEqualTo(13);
        assertThat(tables).isEqualTo(38);
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
                 subtotal,desconto,total_calculado,total_cobrado,version,created_at,updated_at)
                values (?,?,?, ?,?,'PENDING','TERMINAL',3.846500,0.288487,3.558013,3.560000,0,
                        utc_timestamp(6),utc_timestamp(6))
                """, ORDER, EMPRESA_A, CONDOMINIO_A, TERMINAL, CARRINHO);
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
}
