package com.jefiro.app247.infra.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.Pagamento;
import com.jefiro.app247.domain.model.Carrinho;
import com.jefiro.app247.domain.model.dto.OrderResponse;
import com.jefiro.app247.domain.model.dto.PointPaymentResponse;
import com.jefiro.app247.domain.model.terminal.Terminal;
import com.jefiro.app247.domain.model.Condominio;
import com.jefiro.app247.infra.event.PaymentEvent;
import com.jefiro.app247.domain.model.enum_type.PagamentoStatus;
import com.jefiro.app247.domain.model.enum_type.PagamentoTipo;
import com.jefiro.app247.domain.model.enum_type.PaymentMethodId;
import com.jefiro.app247.domain.model.enum_type.order.OrderStatus;
import com.jefiro.app247.infra.repository.PagamentoRepository;
import com.jefiro.app247.infra.exception.UnknownExternalStatusException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PagamentoServiceTest {

    private static final String ORDER_ID = "order-local-1";

    @Mock OrderService orderService;
    @Mock CarrinhoService carrinhoService;
    @Mock PagamentoRepository pagamentoRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock MercadoPagoOrderQueryService mercadoPagoOrderQueryService;
    @Mock PaymentReconciliationService reconciliationService;
    @InjectMocks PagamentoService pagamentoService;

    private Order order;
    private Pagamento pagamento;

    @BeforeEach
    void setUp() throws Exception {
        PaymentStateTransitionService transitionService = new PaymentStateTransitionService();
        transitionService.orderService = orderService;
        transitionService.carrinhoService = carrinhoService;
        transitionService.pagamentoRepository = pagamentoRepository;
        transitionService.eventPublisher = eventPublisher;
        pagamentoService.transitionService = transitionService;
        order = new Order();
        order.setIdOrder(ORDER_ID);
        order.setStatus(OrderStatus.PENDING);
        order.setCarrinho(new Carrinho());
        pagamento = new Pagamento();
        order.setPagamento(pagamento);

        var mapper = PagamentoService.class.getDeclaredField("mapper");
        mapper.setAccessible(true);
        mapper.set(pagamentoService, new ObjectMapper());
    }

    @Test
    void pagamentoAprovadoComCredito() throws Exception {
        process("processed", "accredited", "credit_card", "visa", 1, true);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PROCESSED);
        assertThat(pagamento.getStatus()).isEqualTo(PagamentoStatus.PROCESSED);
        assertThat(pagamento.getTipo()).isEqualTo(PagamentoTipo.CREDIT_CARD);
        assertThat(pagamento.getPaymentMethodId()).isEqualTo(PaymentMethodId.VISA);
        assertThat(pagamento.getInstallments()).isEqualTo(1);
        assertThat(pagamento.getPaidAt()).isNotNull();
        assertThat(order.getPaidAt()).isNotNull();
        assertThat(order.getCarrinho().getStatus()).isEqualTo(
                com.jefiro.app247.domain.model.enum_type.CarrinhoStatus.PAID);
    }

    @Test
    void pagamentoAprovadoComDebito() throws Exception {
        process("processed", "accredited", "debit_card", "debvisa", 1, true);

        assertThat(pagamento.getStatus()).isEqualTo(PagamentoStatus.PROCESSED);
        assertThat(pagamento.getTipo()).isEqualTo(PagamentoTipo.DEBIT_CARD);
        assertThat(pagamento.getPaymentMethodId()).isEqualTo(PaymentMethodId.DEBVISA);
    }

    @Test
    void cartaoRecusadoUsaStatusFailedEDetalheDaRecusa() throws Exception {
        when(orderService.getOrderForUpdate(ORDER_ID)).thenReturn(order);
        when(pagamentoRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(orderService.save(any())).thenAnswer(call -> call.getArgument(0));
        String json = webhook("failed", "insufficient_amount", "credit_card", "visa", 1, true)
                .replaceFirst("\\\"status_detail\\\":\\\"insufficient_amount\\\"",
                        "\\\"status_detail\\\":\\\"failed\\\"");

        pagamentoService.atualizarPagamento(json);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(pagamento.getStatus()).isEqualTo(PagamentoStatus.FAILED);
        assertThat(pagamento.getStatusDetail()).isEqualTo("insufficient_amount");
        assertThat(order.getMpStatusDetail()).isEqualTo(
                com.jefiro.app247.domain.model.enum_type.order.StatusDetail.FAILED
        );
    }

    @Test
    void cancelamentoAtualizaPagamentoEPublicaEvento() throws Exception {
        process("canceled", "canceled", null, null, null, true);

        assertThat(pagamento.getStatus()).isEqualTo(PagamentoStatus.CANCELED);
        var captor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(2)).publishEvent(captor.capture());
        assertThat(captor.getAllValues()).anyMatch(
                com.jefiro.app247.infra.event.CompraCanceladaEvent.class::isInstance);
        assertThat(captor.getAllValues()).anyMatch(PaymentEvent.class::isInstance);
    }

    @Test
    void orderAguardandoNoTerminalMantemPagamentoPendente() throws Exception {
        process("at_terminal", "at_terminal", null, null, null, false);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.AT_TERMINAL);
        assertThat(pagamento.getStatus()).isEqualTo(PagamentoStatus.PENDING);
    }

    @Test
    void expiracaoAtualizaPagamento() throws Exception {
        process("expired", "expired", null, null, null, false);
        assertThat(pagamento.getStatus()).isEqualTo(PagamentoStatus.EXPIRED);
    }

    @Test
    void reembolsoSemTransactionsEhAceito() throws Exception {
        process("refunded", "refunded", null, null, null, false);
        assertThat(pagamento.getStatus()).isEqualTo(PagamentoStatus.REFUNDED);
    }

    @Test
    void actionRequiredSemCamposOpcionaisEhAceito() throws Exception {
        process("action_required", null, null, null, null, false);
        assertThat(pagamento.getStatus()).isEqualTo(PagamentoStatus.ACTION_REQUIRED);
        assertThat(pagamento.getStatusDetail()).isNull();
    }

    @Test
    void statusDesconhecidoNaoAlteraNemPersisteEntidades() throws Exception {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> pagamentoService.atualizarPagamento(
                        webhook("future_status", "future_detail", null, null, null, false)))
                .isInstanceOf(UnknownExternalStatusException.class);

        assertThat(pagamento.getStatus()).isEqualTo(PagamentoStatus.PENDING);
        verifyNoInteractions(orderService, pagamentoRepository);
    }

    @Test
    void processedSemPaymentMethodNaoFalha() throws Exception {
        process("processed", "accredited", null, null, null, true);
        assertThat(pagamento.getStatus()).isEqualTo(PagamentoStatus.PROCESSED);
        assertThat(pagamento.getTipo()).isNull();
    }

    @Test
    void webhookResumidoConsultaOrderAntesDeAtualizar() throws Exception {
        OrderResponse response = new ObjectMapper().readValue("""
                {
                  "id":"mp-order-1",
                  "type":"point",
                  "external_reference":"order-local-1",
                  "status":"expired",
                  "status_detail":"expired"
                }
                """, OrderResponse.class);
        when(mercadoPagoOrderQueryService.getOrder("mp-user-1", "mp-order-1"))
                .thenReturn(response);
        when(orderService.getOrderForUpdate(ORDER_ID)).thenReturn(order);
        when(pagamentoRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(orderService.save(any())).thenAnswer(call -> call.getArgument(0));

        pagamentoService.atualizarPagamento("""
                {
                  "action":"order.expired",
                  "data":{"id":"mp-order-1"},
                  "type":"order",
                  "user_id":"mp-user-1"
                }
                """);

        assertThat(pagamento.getStatus()).isEqualTo(PagamentoStatus.EXPIRED);
        verify(mercadoPagoOrderQueryService).getOrder("mp-user-1", "mp-order-1");
    }

    @Test
    void webhookComVersaoAntigaNaoRegridePagamentoProcessado() throws Exception {
        order.setStatus(OrderStatus.PROCESSED);
        order.setMpEventVersion(4);
        pagamento.setStatus(PagamentoStatus.PROCESSED);
        when(orderService.getOrderForUpdate(ORDER_ID)).thenReturn(order);

        pagamentoService.atualizarPagamento(
                webhook("failed", "processing_error", null, null, null, false)
                        .replace("\"version\":3", "\"version\":2")
        );

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PROCESSED);
        assertThat(pagamento.getStatus()).isEqualTo(PagamentoStatus.PROCESSED);
        verify(pagamentoRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void statusProcessadoNaoRegrideMesmoComVersaoMaior() throws Exception {
        order.setStatus(OrderStatus.PROCESSED);
        order.setMpEventVersion(3);
        pagamento.setStatus(PagamentoStatus.PROCESSED);
        when(orderService.getOrderForUpdate(ORDER_ID)).thenReturn(order);

        pagamentoService.atualizarPagamento(
                webhook("failed", "processing_error", null, null, null, false)
                        .replace("\"version\":3", "\"version\":4")
        );

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PROCESSED);
        assertThat(pagamento.getStatus()).isEqualTo(PagamentoStatus.PROCESSED);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void aprovacaoReconciliadaDuasVezesPublicaEfeitoDeNegocioUmaVez() throws Exception {
        when(orderService.getOrderForUpdate(ORDER_ID)).thenReturn(order);
        when(pagamentoRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(orderService.save(any())).thenAnswer(call -> call.getArgument(0));
        String approved = webhook("processed", "accredited", "credit_card", "visa", 1, true);

        pagamentoService.atualizarPagamento(approved);
        pagamentoService.atualizarPagamento(approved);

        verify(eventPublisher, times(1)).publishEvent(any(com.jefiro.app247.infra.event.OrderPaidEvent.class));
        verify(eventPublisher, times(1)).publishEvent(any(PaymentEvent.class));
    }

    @Test
    void cliqueDuplicadoRetornaMesmaCobrancaSemCriarOutraNoService() {
        Carrinho carrinho = new Carrinho();
        carrinho.setIdCarrinho("cart-1");
        Terminal terminal = new Terminal();
        terminal.setIdTerminal("terminal-1");
        terminal.setCondominio(new Condominio());
        carrinho.setTerminal(terminal);
        Order existing = new Order();
        existing.setIdOrder(ORDER_ID);
        existing.setStatus(OrderStatus.CREATED);
        existing.setCarrinho(carrinho);
        existing.setMpOrderId("mp-order-1");
        existing.setPagamento(new Pagamento(existing));
        when(carrinhoService.getByIdForUpdate("cart-1")).thenReturn(carrinho);
        when(orderService.criarCobranca(carrinho)).thenReturn(existing);

        PointPaymentResponse first = pagamentoService.gerarCobranca("cart-1");
        PointPaymentResponse second = pagamentoService.gerarCobranca("cart-1");

        assertThat(first.orderId()).isEqualTo(ORDER_ID);
        assertThat(second.status()).isEqualTo(
                com.jefiro.app247.domain.model.enum_type.TerminalPaymentStatus.WAITING_PAYMENT);
        verify(orderService, times(2)).criarCobranca(carrinho);
    }

    @Test
    void tentativaComRemotoAprovadoReconciliaENaoCriaSegundaCobranca() {
        Carrinho carrinho = new Carrinho();
        carrinho.setIdCarrinho("cart-1");
        Terminal terminal = new Terminal();
        terminal.setIdTerminal("terminal-1");
        terminal.setCondominio(new Condominio());
        carrinho.setTerminal(terminal);
        Order existing = new Order();
        existing.setIdOrder(ORDER_ID);
        existing.setStatus(OrderStatus.CREATED);
        existing.setCarrinho(carrinho);
        existing.setMpOrderId("mp-order-1");
        existing.setPagamento(new Pagamento(existing));
        when(orderService.findByCarrinho("cart-1")).thenReturn(java.util.Optional.of(existing));
        doAnswer(call -> {
            existing.setStatus(OrderStatus.PROCESSED);
            existing.getPagamento().setStatus(PagamentoStatus.PROCESSED);
            return true;
        }).when(reconciliationService).reconcileOrder(ORDER_ID);
        when(carrinhoService.getByIdForUpdate("cart-1")).thenReturn(carrinho);
        when(orderService.criarCobranca(carrinho)).thenReturn(existing);

        PointPaymentResponse response = pagamentoService.gerarCobranca("cart-1");

        assertThat(response.status()).isEqualTo(
                com.jefiro.app247.domain.model.enum_type.TerminalPaymentStatus.APPROVED);
        verify(reconciliationService).reconcileOrder(ORDER_ID);
        verify(orderService).criarCobranca(carrinho);
    }

    @Test
    void aprovacaoPublicaStatusParaTerminalDaOrder() throws Exception {
        Terminal terminal = new Terminal();
        terminal.setIdTerminal("terminal-correto");
        order.getCarrinho().setTerminal(terminal);
        process("processed", "accredited", "credit_card", "visa", 1, true);

        var captor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
        PaymentEvent event = captor.getAllValues().stream()
                .filter(PaymentEvent.class::isInstance)
                .map(PaymentEvent.class::cast)
                .findFirst().orElseThrow();
        assertThat(event.getTerminalId()).isEqualTo("terminal-correto");
        assertThat(event.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(event.getStatus()).isEqualTo(
                com.jefiro.app247.domain.model.enum_type.TerminalPaymentStatus.APPROVED);
    }

    private void process(
            String status,
            String statusDetail,
            String methodType,
            String methodId,
            Integer installments,
            boolean includePayment
    ) throws Exception {
        when(orderService.getOrderForUpdate(ORDER_ID)).thenReturn(order);
        when(pagamentoRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(orderService.save(any())).thenAnswer(call -> call.getArgument(0));
        pagamentoService.atualizarPagamento(
                webhook(status, statusDetail, methodType, methodId, installments, includePayment)
        );
    }

    private String webhook(
            String status,
            String statusDetail,
            String methodType,
            String methodId,
            Integer installments,
            boolean includePayment
    ) throws Exception {
        String detail = statusDetail == null ? "null" : '"' + statusDetail + '"';
        String transactions = includePayment ? """
                ,"transactions":{"payments":[{
                  "id":"payment-1",
                  "status":"%s",
                  "status_detail":%s%s
                }]}
                """.formatted(status, detail, methodType == null ? "" : """
                ,"payment_method":{"id":"%s","installments":%s,"type":"%s"}
                """.formatted(methodId, installments, methodType)) : "";
        return """
                {
                  "action":"order.%s",
                  "data":{
                    "external_reference":"%s",
                    "id":"mp-order-1",
                    "status":"%s",
                    "status_detail":%s,
                    "type":"point",
                    "version":3%s
                  },
                  "type":"order",
                  "user_id":"mp-user-1"
                }
                """.formatted(status, ORDER_ID, status, detail, transactions);
    }
}
