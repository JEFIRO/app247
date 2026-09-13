package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.AuditLog;
import com.jefiro.app247.domain.model.dto.admin.AdminSalesSummaryResponse;
import com.jefiro.app247.domain.model.enum_type.PagamentoStatus;
import com.jefiro.app247.domain.model.enum_type.TelemetryAlertStatus;
import com.jefiro.app247.domain.model.enum_type.TelemetryAlertType;
import com.jefiro.app247.domain.model.enum_type.TerminalOperationalStatus;
import com.jefiro.app247.domain.model.enum_type.order.OrderStatus;
import com.jefiro.app247.infra.dto.mercadopago.MercadoPagoSetupStatusResponse;
import com.jefiro.app247.infra.repository.*;
import com.jefiro.app247.infra.service.admin.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminQueryServicesTest {
    @BeforeEach
    void setUp() {
        EmpresaContext.set("empresa-a");
    }

    @AfterEach
    void tearDown() {
        EmpresaContext.clear();
    }

    @Test
    void vendasUsamTenantStatusPagoETicketCalculadoNoBackend() {
        OrderRepository repository = mock(OrderRepository.class);
        OrderRepository.SalesAggregation aggregation = mock(OrderRepository.SalesAggregation.class);
        when(aggregation.getQuantidadeVendas()).thenReturn(2L);
        when(aggregation.getFaturamento()).thenReturn(new BigDecimal("45.810000"));
        when(repository.summarizeSales(eq("empresa-a"), eq(OrderStatus.PROCESSED), any(), any()))
                .thenReturn(aggregation);
        AdminSalesQueryService service = new AdminSalesQueryService();
        ReflectionTestUtils.setField(service, "orderRepository", repository);

        AdminSalesSummaryResponse response = service.summary(
                new TimePolicy.DateRange(Instant.parse("2026-09-04T03:00:00Z"),
                        Instant.parse("2026-09-05T03:00:00Z")));

        assertThat(response.quantidadeVendas()).isEqualTo(2);
        assertThat(response.faturamento()).isEqualByComparingTo("45.810000");
        assertThat(response.ticketMedio()).isEqualByComparingTo("22.905000");
        verify(repository).summarizeSales(eq("empresa-a"), eq(OrderStatus.PROCESSED), any(), any());
    }

    @Test
    void terminalResumoUsaThresholdConfiguradoETenant() {
        TerminalRepository terminals = mock(TerminalRepository.class);
        TerminalTelemetryAlertRepository alerts = mock(TerminalTelemetryAlertRepository.class);
        TelemetryThresholds thresholds = mock(TelemetryThresholds.class);
        when(thresholds.getOfflineSeconds()).thenReturn(90L);
        when(terminals.countByCondominioEmpresaIdAndAtivoTrue("empresa-a")).thenReturn(8L);
        when(terminals.countByCondominioEmpresaIdAndAtivoTrueAndLastPingGreaterThanEqual(
                eq("empresa-a"), any())).thenReturn(7L);
        when(alerts.countTerminalsWithAlerts("empresa-a", TelemetryAlertStatus.ACTIVE)).thenReturn(2L);
        AdminTerminalQueryService service = new AdminTerminalQueryService();
        ReflectionTestUtils.setField(service, "terminalRepository", terminals);
        ReflectionTestUtils.setField(service, "alertRepository", alerts);
        ReflectionTestUtils.setField(service, "thresholds", thresholds);

        var response = service.summary();

        assertThat(response.total()).isEqualTo(8);
        assertThat(response.online()).isEqualTo(7);
        assertThat(response.offline()).isEqualTo(1);
        assertThat(response.comAlerta()).isEqualTo(2);
    }

    @Test
    void alertasSaoAgregadosSomenteDentroDoTenant() {
        TerminalTelemetryAlertRepository repository = mock(TerminalTelemetryAlertRepository.class);
        when(repository.countByTerminalCondominioEmpresaIdAndStatus(
                "empresa-a", TelemetryAlertStatus.ACTIVE)).thenReturn(5L);
        when(repository.countCritical(eq("empresa-a"), eq(TelemetryAlertStatus.ACTIVE),
                eq(TelemetryAlertType.TERMINAL_OFFLINE),
                eq(List.of(TerminalOperationalStatus.CRITICO, TerminalOperationalStatus.OFFLINE))))
                .thenReturn(2L);
        AdminAlertQueryService service = new AdminAlertQueryService();
        ReflectionTestUtils.setField(service, "alertRepository", repository);

        var response = service.summary();

        assertThat(response.ativos()).isEqualTo(5);
        assertThat(response.critical()).isEqualTo(2);
        assertThat(response.warning()).isEqualTo(3);
    }

    @Test
    void pagamentoRecenteNaoViraAtencaoAntesDoLimite() {
        PagamentoRepository repository = mock(PagamentoRepository.class);
        when(repository.countAttention(eq("empresa-a"), any(), eq(PagamentoStatus.PENDING),
                eq(PagamentoStatus.ACTION_REQUIRED), eq(PagamentoStatus.FAILED),
                eq("processing_error"))).thenReturn(2L);
        AdminPaymentQueryService service = new AdminPaymentQueryService();
        ReflectionTestUtils.setField(service, "pagamentoRepository", repository);
        ReflectionTestUtils.setField(service, "pendingAttentionMinutes", 15L);

        Instant before = Instant.now().minusSeconds(16 * 60);
        var response = service.attention();

        assertThat(response.precisamAtencao()).isEqualTo(2);
        assertThat(response.pendenteAntesDe()).isAfter(before);
        assertThat(response.pendenteAntesDe()).isBefore(Instant.now().minusSeconds(14 * 60));
    }

    @Test
    void checklistEhDerivadoDosAgregadosReaisDoTenant() {
        EmpresaRepository empresas = mock(EmpresaRepository.class);
        CondominioRepository condominios = mock(CondominioRepository.class);
        TerminalRepository terminals = mock(TerminalRepository.class);
        ProdutoRepository produtos = mock(ProdutoRepository.class);
        EstoqueCondominioRepository estoque = mock(EstoqueCondominioRepository.class);
        MercadoPagoSetupStatusService mercadoPago = mock(MercadoPagoSetupStatusService.class);
        when(empresas.existsById("empresa-a")).thenReturn(true);
        when(condominios.existsByEmpresaIdAndAtivoTrue("empresa-a")).thenReturn(true);
        when(terminals.existsByCondominioEmpresaIdAndAtivoTrue("empresa-a")).thenReturn(true);
        when(produtos.existsByEmpresaIdAndAtivoTrue("empresa-a")).thenReturn(true);
        when(estoque.existsByEmpresaIdAndAtivoTrueAndProdutoAtivoTrue("empresa-a")).thenReturn(true);
        when(mercadoPago.consultar()).thenReturn(new MercadoPagoSetupStatusResponse(
                true, false, false, 1, 0, Instant.now()));
        AdminOnboardingService service = new AdminOnboardingService();
        ReflectionTestUtils.setField(service, "empresaRepository", empresas);
        ReflectionTestUtils.setField(service, "condominioRepository", condominios);
        ReflectionTestUtils.setField(service, "terminalRepository", terminals);
        ReflectionTestUtils.setField(service, "produtoRepository", produtos);
        ReflectionTestUtils.setField(service, "estoqueRepository", estoque);
        ReflectionTestUtils.setField(service, "mercadoPagoSetupStatusService", mercadoPago);

        var response = service.status();

        assertThat(response.empresaCadastrada()).isTrue();
        assertThat(response.mercadoPagoVinculado()).isTrue();
        assertThat(response.pointVinculada()).isFalse();
        assertThat(response.produtosAssociadosCondominio()).isTrue();
        assertThat(response.completo()).isFalse();
    }

    @Test
    void atividadeRecenteFiltraAcoesAdministrativasNaQueryDoTenant() {
        AuditLogRepository repository = mock(AuditLogRepository.class);
        AuditLog log = new AuditLog();
        log.setId("audit-1");
        log.setAction("PRODUCT_UPDATED");
        log.setEntityType("Produto");
        log.setEntityId("produto-123456789");
        log.setCreatedAt(Instant.parse("2026-09-05T12:00:00Z"));
        when(repository.findAllByEmpresaIdAndActionInOrderByCreatedAtDesc(
                eq("empresa-a"), argThat(actions -> actions.contains("PRODUCT_UPDATED")), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(log)));
        AdminActivityQueryService service = new AdminActivityQueryService();
        ReflectionTestUtils.setField(service, "auditLogRepository", repository);

        var response = service.recent(8);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).titulo()).isEqualTo("Produto atualizado");
        assertThat(response.get(0).descricao()).isEqualTo("Produto · produto-");
        verify(repository).findAllByEmpresaIdAndActionInOrderByCreatedAtDesc(
                eq("empresa-a"), anyCollection(), argThat(pageable -> pageable.getPageSize() == 8));
    }
}
