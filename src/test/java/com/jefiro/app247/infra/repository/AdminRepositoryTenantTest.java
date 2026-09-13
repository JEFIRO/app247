package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.Carrinho;
import com.jefiro.app247.domain.model.Condominio;
import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.PaymentAttempt;
import com.jefiro.app247.domain.model.enum_type.OriginRequest;
import com.jefiro.app247.domain.model.enum_type.PagamentoStatus;
import com.jefiro.app247.domain.model.enum_type.order.OrderStatus;
import com.jefiro.app247.domain.model.terminal.Terminal;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class AdminRepositoryTenantTest {
    @Autowired EntityManager entityManager;
    @Autowired OrderRepository orderRepository;
    @Autowired PagamentoRepository pagamentoRepository;

    @Test
    void resumoDeVendasConsideraSomenteProcessadasNoTenantEPeriodo() {
        Empresa a = empresa("A", "11111111000111", "admin-a@teste.com");
        Empresa b = empresa("B", "22222222000122", "admin-b@teste.com");
        Terminal terminalA = terminal(a, "A");
        Terminal terminalB = terminal(b, "B");
        Instant paidAt = Instant.parse("2026-09-04T14:00:00Z");
        order(terminalA, OrderStatus.PROCESSED, "10.500000", paidAt);
        order(terminalA, OrderStatus.FAILED, "99.000000", paidAt);
        order(terminalB, OrderStatus.PROCESSED, "88.000000", paidAt);
        entityManager.flush();

        OrderRepository.SalesAggregation row = orderRepository.summarizeSales(
                a.getId(), OrderStatus.PROCESSED,
                Instant.parse("2026-09-04T03:00:00Z"),
                Instant.parse("2026-09-05T03:00:00Z"));

        assertThat(row.getQuantidadeVendas()).isEqualTo(1);
        assertThat(row.getFaturamento()).isEqualByComparingTo("10.500000");
    }

    @Test
    void atencaoDePagamentoIgnoraPendenteRecenteEOutroTenant() {
        Empresa a = empresa("A", "33333333000133", "pay-a@teste.com");
        Empresa b = empresa("B", "44444444000144", "pay-b@teste.com");
        PaymentAttempt oldA = attempt(order(terminal(a, "A"), OrderStatus.PENDING,
                "12.000000", null), PagamentoStatus.PENDING);
        attempt(order(terminal(a, "A2"), OrderStatus.PENDING,
                "13.000000", null), PagamentoStatus.PENDING);
        PaymentAttempt oldB = attempt(order(terminal(b, "B"), OrderStatus.PENDING,
                "14.000000", null), PagamentoStatus.PENDING);
        entityManager.flush();
        Instant old = Instant.now().minusSeconds(60 * 60);
        entityManager.createQuery("update PaymentAttempt a set a.updatedAt=:old where a.idPagamento in :ids")
                .setParameter("old", old)
                .setParameter("ids", java.util.List.of(oldA.getIdPagamento(), oldB.getIdPagamento()))
                .executeUpdate();
        entityManager.clear();

        long count = pagamentoRepository.countAttention(
                a.getId(), Instant.now().minusSeconds(15 * 60),
                PagamentoStatus.PENDING, PagamentoStatus.ACTION_REQUIRED,
                PagamentoStatus.FAILED, "processing_error");

        assertThat(count).isEqualTo(1);
    }

    @Test
    void historicoDeVendasProcessadasFiltraPelaDataDoPagamento() {
        Empresa empresa = empresa("F", "55555555000155", "sales-f@teste.com");
        Terminal terminal = terminal(empresa, "F");
        Instant inicioHoje = Instant.parse("2026-09-04T03:00:00Z");
        Instant fimHoje = Instant.parse("2026-09-05T03:00:00Z");
        Order pagaHoje = order(terminal, OrderStatus.PROCESSED, "20.000000",
                Instant.parse("2026-09-04T15:00:00Z"));
        pagaHoje.setCreatedAt(Instant.parse("2026-09-02T15:00:00Z"));
        Order pagaAntes = order(terminal, OrderStatus.PROCESSED, "30.000000",
                Instant.parse("2026-09-02T15:00:00Z"));
        pagaAntes.setCreatedAt(Instant.parse("2026-09-04T16:00:00Z"));
        entityManager.flush();

        var pagina = orderRepository.findAdminSales(
                empresa.getId(), inicioHoje, fimHoje, null, null,
                OrderStatus.PROCESSED, true, PageRequest.of(0, 20));

        assertThat(pagina.getContent())
                .extracting(com.jefiro.app247.domain.model.dto.admin.AdminSaleResponse::id)
                .containsExactly(pagaHoje.getIdOrder());
    }

    private Empresa empresa(String suffix, String cnpj, String email) {
        Empresa empresa = Empresa.builder()
                .razaoSocial("Empresa " + suffix).nomeFantasia("Empresa " + suffix)
                .cnpj(cnpj).email(email).tenantId("tenant-" + suffix + "-" + cnpj)
                .ativo(true).build();
        entityManager.persist(empresa);
        return empresa;
    }

    private Terminal terminal(Empresa empresa, String suffix) {
        Condominio condominio = new Condominio();
        condominio.setEmpresa(empresa);
        condominio.setNome("Condomínio " + suffix);
        condominio.setAtivo(true);
        entityManager.persist(condominio);
        Terminal terminal = new Terminal();
        terminal.setCondominio(condominio);
        terminal.setNome("Terminal " + suffix);
        terminal.setCodigo("TERM-" + suffix);
        terminal.setAtivo(true);
        entityManager.persist(terminal);
        return terminal;
    }

    private Order order(Terminal terminal, OrderStatus status, String total, Instant paidAt) {
        Carrinho carrinho = new Carrinho();
        carrinho.setTerminal(terminal);
        carrinho.setSubtotal(new BigDecimal(total));
        entityManager.persist(carrinho);
        Order order = new Order();
        order.setEmpresa(terminal.getCondominio().getEmpresa());
        order.setCondominio(terminal.getCondominio());
        order.setTerminal(terminal);
        order.setCarrinho(carrinho);
        order.setSubtotal(new BigDecimal(total));
        order.setDesconto(BigDecimal.ZERO.setScale(6));
        order.setTotalCalculado(new BigDecimal(total));
        order.setTotalCobrado(new BigDecimal(total));
        order.setOriginRequest(OriginRequest.TERMINAL);
        order.setStatus(status);
        order.setPaidAt(paidAt);
        entityManager.persist(order);
        return order;
    }

    private PaymentAttempt attempt(Order order, PagamentoStatus status) {
        PaymentAttempt attempt = new PaymentAttempt(order);
        attempt.setStatus(status);
        attempt.setIdempotencyKey("idem-" + java.util.UUID.randomUUID());
        attempt.setExternalReference("ref-" + java.util.UUID.randomUUID());
        entityManager.persist(attempt);
        return attempt;
    }
}
