package com.jefiro.app247.infra.service.comprovante;

import com.jefiro.app247.domain.model.Condominio;
import com.jefiro.app247.domain.model.CustomerIdentity;
import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.OrderItem;
import com.jefiro.app247.domain.model.PaymentAttempt;
import com.jefiro.app247.domain.model.appclient.CustomerCoupon;
import com.jefiro.app247.domain.model.appclient.LoyaltyMovement;
import com.jefiro.app247.domain.model.auth.Endereco;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.dto.comprovante.ComprovanteDigitalResponse;
import com.jefiro.app247.domain.model.dto.comprovante.ComprovanteDigitalResponse.Desconto;
import com.jefiro.app247.domain.model.enum_type.PagamentoStatus;
import com.jefiro.app247.domain.model.enum_type.PagamentoTipo;
import com.jefiro.app247.domain.model.enum_type.PaymentMethodId;
import com.jefiro.app247.domain.model.enum_type.UnidadeMedida;
import com.jefiro.app247.domain.model.enum_type.order.OrderStatus;
import com.jefiro.app247.domain.model.terminal.Terminal;
import com.jefiro.app247.infra.exception.ApiBusinessException;
import com.jefiro.app247.infra.repository.LoyaltyMovementRepository;
import com.jefiro.app247.infra.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ComprovanteDigitalServiceTest {
    private final OrderService orderService = mock(OrderService.class);
    private final LoyaltyMovementRepository movements = mock(LoyaltyMovementRepository.class);
    private final ComprovanteDigitalService service = new ComprovanteDigitalService(orderService, movements);
    private Order order;

    @BeforeEach
    void setUp() {
        order = approvedOrder();
        when(movements.findByIdempotencyKey("purchase:order-1284")).thenReturn(Optional.empty());
    }

    @Test
    void devolveIdentificacaoCompraTerminalEPagamento() {
        ComprovanteDigitalResponse receipt = service.montar(order);

        assertThat(receipt.estabelecimento().nome()).isEqualTo("Mercado Autônomo");
        assertThat(receipt.estabelecimento().razaoSocial()).isEqualTo("Mercado Autônomo LTDA");
        assertThat(receipt.estabelecimento().cnpj()).isEqualTo("12.345.678/0001-90");
        assertThat(receipt.estabelecimento().endereco()).isEqualTo("Rua das Flores, 123 - Centro");
        assertThat(receipt.estabelecimento().local()).isEqualTo("Condomínio X - Feira de Santana/BA");
        assertThat(receipt.estabelecimento().logoUrl()).isEqualTo("https://cdn.example.test/logo.png");
        assertThat(receipt.numeroCompra()).isEqualTo("order-1284");
        assertThat(receipt.pagoEm()).isEqualTo(Instant.parse("2026-09-12T18:40:00Z"));
        assertThat(receipt.data()).isEqualTo("12/09/2026");
        assertThat(receipt.hora()).isEqualTo("15:40");
        assertThat(receipt.terminal().codigo()).isEqualTo("TERM-001");
        assertThat(receipt.terminal().nome()).isEqualTo("Totem 01");
        assertThat(receipt.pagamento().forma()).isEqualTo("CRÉDITO");
        assertThat(receipt.pagamento().bandeira()).isEqualTo("VISA");
        assertThat(receipt.pagamento().parcelas()).isEqualTo(2);
        assertThat(receipt.pagamento().status()).isEqualTo("APROVADO");
        assertThat(receipt.pagamento().transacaoId()).isEqualTo("mp-777");
        assertThat(receipt.pagamento().nsu()).isEqualTo("123456");
        assertThat(receipt.pagamento().autorizacao()).isEqualTo("987654");
        assertThat(receipt.qrCodeUrl()).isNull();
        assertThat(receipt.aviso()).contains("não é documento fiscal");
    }

    @Test
    void itemMostraValorOriginalEDescontosFechamComOTotal() {
        CustomerCoupon coupon = new CustomerCoupon();
        coupon.setNome("BEMVINDO");
        order.setCoupon(coupon);
        order.setDescontoCupom(new BigDecimal("3.000000"));
        order.setTotalCobrado(new BigDecimal("34.900000"));

        ComprovanteDigitalResponse receipt = service.montar(order);

        var promoted = receipt.itens().get(0);
        assertThat(promoted.descricao()).isEqualTo("Sanduíche");
        assertThat(promoted.quantidade()).isEqualByComparingTo("2");
        assertThat(promoted.valorUnitario()).isEqualByComparingTo("7.45");
        assertThat(promoted.subtotal()).isEqualByComparingTo("14.90");
        assertThat(promoted.desconto()).isEqualByComparingTo("2.00");
        assertThat(promoted.valorFinal()).isEqualByComparingTo("12.90");
        assertThat(receipt.subtotal()).isEqualByComparingTo("39.90");
        assertThat(receipt.descontos()).containsExactly(
                new Desconto("PROMOCAO", "Promoções", new BigDecimal("2.00")),
                new Desconto("CUPOM", "Cupom BEMVINDO", new BigDecimal("3.00")));
        assertThat(receipt.descontoTotal()).isEqualByComparingTo("5.00");
        assertThat(receipt.arredondamento()).isEqualByComparingTo("0");
        assertThat(receipt.total()).isEqualByComparingTo("34.90");
    }

    @Test
    void compraSemDescontoDevolveListaVazia() {
        order.setItems(List.of(item("Água", "1.000", "3.500000", "3.500000")));
        order.setTotalCobrado(new BigDecimal("3.500000"));

        ComprovanteDigitalResponse receipt = service.montar(order);

        assertThat(receipt.descontos()).isEmpty();
        assertThat(receipt.descontoTotal()).isEqualByComparingTo("0");
        assertThat(receipt.subtotal()).isEqualByComparingTo("3.50");
    }

    @Test
    void aceitaQuantidadeFracionadaERegistraArredondamento() {
        OrderItem weighed = item("Queijo", "0.333", "10.010000", "3.333330");
        order.setItems(List.of(weighed));
        order.setTotalCobrado(new BigDecimal("3.340000"));

        ComprovanteDigitalResponse receipt = service.montar(order);

        assertThat(receipt.itens().get(0).quantidade()).isEqualByComparingTo("0.333");
        assertThat(receipt.subtotal()).isEqualByComparingTo("3.33");
        assertThat(receipt.arredondamento()).isEqualByComparingTo("0.01");
        assertThat(receipt.total()).isEqualByComparingTo("3.34");
    }

    @Test
    void compraVinculadaExpoeSomentePrimeiroNomeEPontosGanhos() {
        User user = new User();
        user.setNome("Maria Souza Lima");
        order.setUser(user);
        LoyaltyMovement movement = new LoyaltyMovement();
        movement.setPointsDelta(34L);
        when(movements.findByIdempotencyKey("purchase:order-1284")).thenReturn(Optional.of(movement));

        ComprovanteDigitalResponse receipt = service.montar(order);

        assertThat(receipt.cliente().vinculado()).isTrue();
        assertThat(receipt.cliente().primeiroNome()).isEqualTo("Maria");
        assertThat(receipt.fidelidade().pontosGanhos()).isEqualTo(34L);
        assertThat(receipt.fidelidade().cashbackGanho()).isNull();
    }

    @Test
    void devolveCashbackQuandoEsseEhOModoDaEmpresa() {
        LoyaltyMovement movement = new LoyaltyMovement();
        movement.setCashbackDelta(new BigDecimal("1.745000"));
        when(movements.findByIdempotencyKey("purchase:order-1284")).thenReturn(Optional.of(movement));

        ComprovanteDigitalResponse receipt = service.montar(order);

        assertThat(receipt.fidelidade().pontosGanhos()).isNull();
        assertThat(receipt.fidelidade().cashbackGanho()).isEqualByComparingTo("1.75");
    }

    @Test
    void compraSemContaNaoEhVinculadaENaoTemBeneficio() {
        ComprovanteDigitalResponse receipt = service.montar(order);

        assertThat(receipt.cliente().vinculado()).isFalse();
        assertThat(receipt.cliente().primeiroNome()).isNull();
        assertThat(receipt.fidelidade()).isNull();
    }

    @Test
    void compraReivindicadaPorCpfConstaVinculadaSemBeneficio() {
        User user = new User();
        user.setNome("Maria");
        CustomerIdentity identity = new CustomerIdentity();
        identity.setUser(user);
        order.setCustomerIdentity(identity);

        ComprovanteDigitalResponse receipt = service.montar(order);

        assertThat(receipt.cliente().vinculado()).isTrue();
        assertThat(receipt.fidelidade()).isNull();
    }

    @Test
    void logoNaoCadastradaEPixSemBandeiraFicamNulos() {
        order.getEmpresa().setLogoUrl(" ");
        order.getEmpresa().setLogoDarkUrl(null);
        order.getPagamento().setTipo(PagamentoTipo.PIX);
        order.getPagamento().setPaymentMethodId(PaymentMethodId.PIX);

        ComprovanteDigitalResponse receipt = service.montar(order);

        assertThat(receipt.estabelecimento().logoUrl()).isNull();
        assertThat(receipt.estabelecimento().logoDarkUrl()).isNull();
        assertThat(receipt.pagamento().forma()).isEqualTo("PIX");
        assertThat(receipt.pagamento().bandeira()).isNull();
    }

    @Test
    void bandeiraDeDebitoNaoExpoeOPrefixoInterno() {
        order.getPagamento().setTipo(PagamentoTipo.DEBIT_CARD);
        order.getPagamento().setPaymentMethodId(PaymentMethodId.DEBMASTER);

        assertThat(service.montar(order).pagamento().bandeira()).isEqualTo("MASTER");
    }

    @Test
    void pedidoNaoAprovadoNaoGeraComprovante() {
        order.setStatus(OrderStatus.REFUNDED);

        assertThatThrownBy(() -> service.montar(order))
                .isInstanceOf(ApiBusinessException.class)
                .hasMessageContaining("pedido aprovado");
    }

    @Test
    void terminalCarregaAOrderPelaChecagemDeTerminal() {
        when(orderService.getOrderForTerminal("order-1284", "terminal-id")).thenReturn(order);

        assertThat(service.paraTerminal("order-1284", "terminal-id").numeroCompra()).isEqualTo("order-1284");
    }

    private Order approvedOrder() {
        Empresa company = new Empresa();
        company.setNomeFantasia("Mercado Autônomo");
        company.setRazaoSocial("Mercado Autônomo LTDA");
        company.setCnpj("12.345.678/0001-90");
        company.setLogoUrl("https://cdn.example.test/logo.png");

        Endereco address = new Endereco();
        address.setRua("Rua das Flores");
        address.setNumero("123");
        address.setBairro("Centro");
        address.setCidade("Feira de Santana");
        address.setEstado("ba");

        Condominio condominium = new Condominio();
        condominium.setNome("Condomínio X");
        condominium.setEndereco(address);

        Terminal terminal = new Terminal();
        terminal.setCodigo("TERM-001");
        terminal.setNome("Totem 01");

        Order value = new Order();
        value.setIdOrder("order-1284");
        value.setEmpresa(company);
        value.setCondominio(condominium);
        value.setTerminal(terminal);
        value.setItems(List.of(
                item("Sanduíche", "2.000", "7.450000", "12.900000"),
                item("Água", "1.000", "7.000000", "7.000000"),
                item("Chocolate", "3.000", "6.000000", "18.000000")));
        value.setDescontoCupom(new BigDecimal("0.000000"));
        value.setTotalCobrado(new BigDecimal("37.900000"));
        value.setStatus(OrderStatus.PROCESSED);

        PaymentAttempt payment = new PaymentAttempt();
        payment.setAttemptNumber(1);
        payment.setTipo(PagamentoTipo.CREDIT_CARD);
        payment.setPaymentMethodId(PaymentMethodId.VISA);
        payment.setInstallments(2);
        payment.setStatus(PagamentoStatus.PROCESSED);
        payment.setPaidAt(Instant.parse("2026-09-12T18:40:00Z"));
        payment.setTransactionId("mp-777");
        payment.setNsu("123456");
        payment.setAuthorizationCode("987654");
        value.setPagamento(payment);
        return value;
    }

    private OrderItem item(String name, String quantity, String originalPrice, String finalValue) {
        OrderItem item = new OrderItem();
        item.setCodigoInterno("SKU-" + name);
        item.setNome(name);
        item.setUnidadeMedida(UnidadeMedida.UN);
        item.setQuantidade(new BigDecimal(quantity));
        item.setPrecoOriginal(new BigDecimal(originalPrice));
        item.setSubtotalCalculado(new BigDecimal(finalValue));
        return item;
    }
}
