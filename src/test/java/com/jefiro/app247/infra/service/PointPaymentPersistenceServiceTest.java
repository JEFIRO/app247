package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Carrinho;
import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.PaymentAttempt;
import com.jefiro.app247.domain.model.dto.OrderResponse;
import com.jefiro.app247.domain.model.enum_type.PagamentoStatus;
import com.jefiro.app247.domain.model.enum_type.order.OrderStatus;
import com.jefiro.app247.infra.exception.ApiBusinessException;
import com.jefiro.app247.infra.repository.OrderRepository;
import com.jefiro.app247.infra.repository.PagamentoRepository;
import com.jefiro.app247.infra.repository.TerminalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import com.jefiro.app247.domain.model.terminal.Terminal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PointPaymentPersistenceServiceTest {
    @Mock CarrinhoService carrinhoService;
    @Mock OrderService orderService;
    @Mock OrderRepository orderRepository;
    @Mock PagamentoRepository pagamentoRepository;
    @Mock MercadoPagoOperationalConfigurationService configurationService;
    @Mock EmpresaService empresaService;
    @Mock TerminalRepository terminalRepository;

    PointPaymentPersistenceService service;
    Order order;
    Carrinho carrinho;

    @BeforeEach
    void setUp() {
        service = new PointPaymentPersistenceService(
                carrinhoService, orderService, orderRepository, pagamentoRepository);
        ReflectionTestUtils.setField(service, "configurationService", configurationService);
        ReflectionTestUtils.setField(service, "empresaService", empresaService);
        ReflectionTestUtils.setField(service, "terminalRepository", terminalRepository);
        carrinho = new Carrinho();
        carrinho.setIdCarrinho("cart-a");
        Terminal terminal = new Terminal();
        terminal.setIdTerminal("terminal-a");
        carrinho.setTerminal(terminal);
        carrinho.setEmpresa(Empresa.builder().id("empresa-a").build());
        order = new Order();
        order.setIdOrder("order-a");
        order.setEmpresa(Empresa.builder().id("empresa-a").build());
        order.setCarrinho(carrinho);
        order.setStatus(OrderStatus.PENDING);
        order.setTotal(new BigDecimal("10.00"));
        order.setTotalCobrado(new BigDecimal("10.00"));
        lenient().when(terminalRepository.findByIdForPaymentUpdate("terminal-a"))
                .thenReturn(Optional.of(terminal));
        lenient().when(orderRepository.findUnresolvedPaymentOrderIdsForTerminal(
                eq("terminal-a"), anyList(), any())).thenReturn(List.of());
    }

    @Test
    void contaDesvinculadaFalhaAntesDeCriarOrderOuPagamento() {
        when(carrinhoService.getByIdForUpdate("cart-a")).thenReturn(carrinho);
        doThrow(new ApiBusinessException(
                org.springframework.http.HttpStatus.CONFLICT,
                "MERCADO_PAGO_NOT_CONFIGURED",
                "Mercado Pago não configurado"))
                .when(configurationService).requireConfigured(carrinho.getTerminal(), "empresa-a");

        assertThatThrownBy(() -> service.prepare("cart-a"))
                .isInstanceOf(ApiBusinessException.class)
                .extracting(ex -> ((ApiBusinessException) ex).getCode())
                .isEqualTo("MERCADO_PAGO_NOT_CONFIGURED");

        verifyNoInteractions(orderService, orderRepository, pagamentoRepository);
    }

    @Test
    void transacaoAPreparaPagamentoPendenteAntesDeLiberarPostRemoto() {
        when(carrinhoService.getByIdForUpdate("cart-a")).thenReturn(carrinho);
        when(orderService.criarCobranca(carrinho)).thenReturn(order);
        when(pagamentoRepository.save(any(PaymentAttempt.class))).thenAnswer(call -> {
            PaymentAttempt pagamento = call.getArgument(0);
            pagamento.setIdPagamento("payment-a");
            return pagamento;
        });
        when(orderRepository.saveAndFlush(order)).thenReturn(order);

        var result = service.prepare("cart-a");

        assertThat(result.shouldSubmit()).isTrue();
        assertThat(order.getPagamento()).isNotNull();
        assertThat(order.getPagamento().getIdPagamento()).isEqualTo("payment-a");
        assertThat(order.getPagamento().getStatus()).isEqualTo(PagamentoStatus.PENDING);
        assertThat(order.getPagamento().getOrder()).isSameAs(order);
        verify(orderRepository).saveAndFlush(order);
        verify(pagamentoRepository).flush();
    }

    @Test
    void transacaoBPersisteIdRemotoSemSubstituirPagamentoLocal() {
        PaymentAttempt pagamento = new PaymentAttempt(order);
        pagamento.setIdPagamento("payment-a");
        order.setPagamento(pagamento);
        when(orderService.getOrderForUpdate("order-a")).thenReturn(order);
        when(orderRepository.saveAndFlush(order)).thenReturn(order);
        OrderResponse remote = new OrderResponse(
                "mp-order-a", "point", "mp-user-a", "order-a", null, null,
                null, null, null, "created", "created", null,
                "2026-08-30T20:00:00Z", 1, null,
                new OrderResponse.Transactions(List.of(
                        new OrderResponse.Payment("transaction-a", "10.00", "created", null, null))));

        var result = service.persistRemoteAcceptance("order-a", remote);

        assertThat(result.orderId()).isEqualTo("order-a");
        assertThat(order.getMpOrderId()).isEqualTo("mp-order-a");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.getPagamento()).isSameAs(pagamento);
        assertThat(pagamento.getTransactionId()).isEqualTo("transaction-a");
        verify(pagamentoRepository).save(pagamento);
    }

    @Test
    void respostaCreatedAtrasadaNaoRegrideWebhookCanceladoQueChegouPrimeiro() {
        PaymentAttempt pagamento = new PaymentAttempt(order);
        pagamento.setIdPagamento("payment-a");
        pagamento.setStatus(PagamentoStatus.CANCELED);
        order.setPagamento(pagamento);
        order.setStatus(OrderStatus.CANCELED);
        order.setMpStatus(OrderStatus.CANCELED);
        order.setMpOrderId("mp-order-a");
        when(orderService.getOrderForUpdate("order-a")).thenReturn(order);
        when(orderRepository.saveAndFlush(order)).thenReturn(order);
        OrderResponse remote = new OrderResponse(
                "mp-order-a", "point", "mp-user-a", "order-a", null, null,
                null, null, null, "created", "created", null,
                "2026-08-30T20:00:00Z", 1, null, null);

        service.persistRemoteAcceptance("order-a", remote);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(order.getMpStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(pagamento.getStatus()).isEqualTo(PagamentoStatus.CANCELED);
    }

    @Test
    void repeticaoIdempotentePodePersistirIdDeOrderJaProcessadaSemInventarEstadoLocal() {
        PaymentAttempt pagamento = new PaymentAttempt(order);
        pagamento.setIdPagamento("payment-a");
        order.setPagamento(pagamento);
        when(orderService.getOrderForUpdate("order-a")).thenReturn(order);
        when(orderRepository.saveAndFlush(order)).thenReturn(order);
        OrderResponse remote = new OrderResponse(
                "mp-order-a", "point", "mp-user-a", "order-a", null, null,
                null, null, null, "processed", "accredited", null,
                "2026-08-30T20:00:00Z", 2, null,
                new OrderResponse.Transactions(List.of(
                        new OrderResponse.Payment("transaction-a", "10.00", "processed", "accredited", null))));

        service.persistRemoteAcceptance("order-a", remote);

        assertThat(order.getMpOrderId()).isEqualTo("mp-order-a");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(pagamento.getTransactionId()).isEqualTo("transaction-a");
    }

    @Test
    void statusRemotoDesconhecidoAindaPersisteIdParaFuturaReconciliacao() {
        PaymentAttempt pagamento = new PaymentAttempt(order);
        pagamento.setIdPagamento("payment-a");
        order.setPagamento(pagamento);
        when(orderService.getOrderForUpdate("order-a")).thenReturn(order);
        when(orderRepository.saveAndFlush(order)).thenReturn(order);
        OrderResponse remote = new OrderResponse(
                "mp-order-a", "point", "mp-user-a", "order-a", null, null,
                null, null, null, "future_status", "future_detail", null,
                "2026-08-30T20:00:00Z", 3, null, null);

        service.persistRemoteAcceptance("order-a", remote);

        assertThat(order.getMpOrderId()).isEqualTo("mp-order-a");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(orderRepository).saveAndFlush(order);
    }

    @Test
    void outraOrderPendenteDoMesmoTerminalBloqueiaNovaTentativa() {
        Order active = new Order();
        active.setIdOrder("order-active");
        active.setCarrinho(carrinho);
        active.setStatus(OrderStatus.CREATED);
        PaymentAttempt attempt = new PaymentAttempt(active);
        attempt.setIdPagamento("attempt-active");
        active.setPagamento(attempt);

        when(carrinhoService.getByIdForUpdate("cart-a")).thenReturn(carrinho);
        when(orderRepository.findUnresolvedPaymentOrderIdsForTerminal(
                eq("terminal-a"), anyList(), any())).thenReturn(List.of("order-active"));
        when(orderService.getOrderForReconciliation("order-active")).thenReturn(active);

        assertThatThrownBy(() -> service.prepare("cart-a"))
                .isInstanceOf(com.jefiro.app247.infra.exception.PaymentAlreadyActiveException.class)
                .satisfies(error -> {
                    var conflict = (com.jefiro.app247.infra.exception.PaymentAlreadyActiveException) error;
                    assertThat(conflict.getOrderId()).isEqualTo("order-active");
                    assertThat(conflict.getPaymentAttemptId()).isEqualTo("attempt-active");
                });

        verify(orderService, never()).criarCobranca(any());
        verify(pagamentoRepository, never()).save(any());
    }

    @Test
    void inicioDeCadaSubmissaoAtualizaCooldownAntesDaChamadaExterna() {
        PaymentAttempt pagamento = new PaymentAttempt(order);
        pagamento.setIdPagamento("payment-a");
        pagamento.setUpdatedAt(Instant.EPOCH);
        order.setPagamento(pagamento);
        when(orderService.getOrderForUpdate("order-a")).thenReturn(order);
        when(pagamentoRepository.saveAndFlush(pagamento)).thenReturn(pagamento);

        assertThat(service.markSubmissionStarted("order-a")).isTrue();

        assertThat(pagamento.getUpdatedAt()).isAfter(Instant.EPOCH);
        verify(pagamentoRepository).saveAndFlush(pagamento);
    }
}
