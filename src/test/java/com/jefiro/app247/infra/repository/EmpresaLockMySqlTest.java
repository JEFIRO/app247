package com.jefiro.app247.infra.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cobranças da mesma Empresa não se bloqueiam, e encerramento/unlink continua esperando por elas. */
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "payment.reconciliation.initial-delay-ms=3600000",
        "terminal.telemetry.offline-check-delay-ms=3600000"
})
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class EmpresaLockMySqlTest {
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

    @Autowired EmpresaRepository empresas;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transaction;

    @Test
    void lockCompartilhadoConviveComOutraCobrancaEBloqueiaEncerramento() throws Exception {
        String empresaId = novaEmpresa();
        CountDownLatch primeiraCobrancaComLock = new CountDownLatch(1);
        CountDownLatch liberarPrimeiraCobranca = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            Future<?> primeiraCobranca = executor.submit(() -> transaction.executeWithoutResult(status -> {
                empresas.findByIdForShare(empresaId).orElseThrow();
                primeiraCobrancaComLock.countDown();
                aguardar(liberarPrimeiraCobranca);
            }));
            assertThat(primeiraCobrancaComLock.await(10, TimeUnit.SECONDS)).isTrue();

            Future<?> segundaCobranca = executor.submit(() -> transaction.executeWithoutResult(
                    status -> empresas.findByIdForShare(empresaId).orElseThrow()));
            segundaCobranca.get(10, TimeUnit.SECONDS);

            Future<?> encerramento = executor.submit(() -> transaction.executeWithoutResult(
                    status -> empresas.findByIdForUpdate(empresaId).orElseThrow()));
            assertThatThrownBy(() -> encerramento.get(2, TimeUnit.SECONDS))
                    .isInstanceOf(TimeoutException.class);

            liberarPrimeiraCobranca.countDown();
            primeiraCobranca.get(10, TimeUnit.SECONDS);
            encerramento.get(10, TimeUnit.SECONDS);
        } finally {
            liberarPrimeiraCobranca.countDown();
            executor.shutdownNow();
        }
    }

    private String novaEmpresa() {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                insert into empresa (id, tenant_id, razao_social, nome_fantasia, cnpj, email, ativo,
                                     created_at, updated_at, cor_principal, cor_secundaria, cor_destaque)
                values (?, ?, 'Empresa Lock Ltda', 'Empresa Lock', '12345678000199', 'lock@example.invalid', true,
                        utc_timestamp(6), utc_timestamp(6), '#169DFF', '#62C8FF', '#00D084')
                """, id, UUID.randomUUID().toString());
        return id;
    }

    private static void aguardar(CountDownLatch latch) {
        try {
            assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
    }
}
