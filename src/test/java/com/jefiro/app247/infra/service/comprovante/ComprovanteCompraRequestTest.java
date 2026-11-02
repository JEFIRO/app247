package com.jefiro.app247.infra.service.comprovante;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jefiro.app247.domain.model.Condominio;
import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.OrderItem;
import com.jefiro.app247.domain.model.PaymentAttempt;
import com.jefiro.app247.domain.model.Produto;
import com.jefiro.app247.domain.model.appclient.CustomerCoupon;
import com.jefiro.app247.domain.model.appclient.LoyaltyMovement;
import com.jefiro.app247.domain.model.auth.Endereco;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.enum_type.PagamentoStatus;
import com.jefiro.app247.domain.model.enum_type.PagamentoTipo;
import com.jefiro.app247.domain.model.enum_type.order.OrderStatus;
import com.jefiro.app247.domain.model.terminal.Terminal;
import com.jefiro.app247.infra.exception.ApiBusinessException;
import com.jefiro.app247.infra.repository.LoyaltyMovementRepository;
import com.jefiro.app247.infra.service.OrderService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ComprovanteCompraRequestTest {

    @Test
    void mapeiaSnapshotHistoricoCompletoNoContratoDaFastApi() {
        Order order = approvedOrder();

        ComprovanteCompraRequest receipt = comprovante(order);

        assertThat(receipt.empresa()).isEqualTo("Mercado Autônomo");
        assertThat(receipt.cnpj()).isEqualTo("12.345.678/0001-90");
        assertThat(receipt.endereco()).isEqualTo("Rua das Flores, 123 - Bloco B - Centro");
        assertThat(receipt.local()).isEqualTo("Condomínio X - Feira de Santana/BA");
        assertThat(receipt.pedido()).isEqualTo("order-1284");
        assertThat(receipt.data()).isEqualTo("12/09/2026");
        assertThat(receipt.hora()).isEqualTo("15:40");
        assertThat(receipt.terminal()).isEqualTo("TERM-001");
        assertThat(receipt.total()).isEqualTo("R$ 15,48");
        assertThat(receipt.pagamento()).isEqualTo("PIX");
        assertThat(receipt.status()).isEqualTo("APROVADO");
        assertThat(receipt.qrData()).isEmpty();
        assertThat(receipt.itens()).containsExactly(
                new ItemComprovanteRequest("Coca-Cola 350ml", new BigDecimal("2"), "R$ 11,98", "R$ 5,99"),
                new ItemComprovanteRequest("Água", new BigDecimal("1"), "R$ 3,50", "R$ 3,50")
        );
    }

    @Test
    void usaSubtotalDoOrderItemMesmoQuandoPrecoAtualDoProdutoMudou() {
        Order order = approvedOrder();
        order.getItems().get(0).getProduto().setPreco(new BigDecimal("99.99"));

        ComprovanteCompraRequest receipt = comprovante(order);

        assertThat(receipt.itens().get(0).valor()).isEqualTo("R$ 11,98");
    }

    @Test
    void enviaValorOriginalNaLinhaEDescontosNosDetalhes() {
        Order order = approvedOrder();
        order.getItems().get(0).setSubtotalCalculado(new BigDecimal("9.980000"));
        CustomerCoupon coupon = new CustomerCoupon();
        coupon.setNome("BEMVINDO");
        order.setCoupon(coupon);
        order.setDescontoCupom(new BigDecimal("1.000000"));
        order.setTotalCobrado(new BigDecimal("12.480000"));

        ComprovanteCompraRequest receipt = comprovante(order);

        assertThat(receipt.itens().get(0).valor()).isEqualTo("R$ 11,98");
        assertThat(receipt.detalhes().subtotal()).isEqualTo("R$ 15,48");
        assertThat(receipt.detalhes().descontos()).containsExactly(
                new DescontoComprovanteRequest("Promoções", "R$ 2,00"),
                new DescontoComprovanteRequest("Cupom BEMVINDO", "R$ 1,00"));
        assertThat(receipt.detalhes().arredondamento()).isNull();
        assertThat(receipt.total()).isEqualTo("R$ 12,48");
    }

    @Test
    void enviaPagamentoClienteFidelidadeContatoELogo() {
        Order order = approvedOrder();
        order.getEmpresa().setTelefone("(75) 3333-3333");
        order.getEmpresa().setEmail("contato@empresa.test");
        order.getEmpresa().setLogoUrl("https://cdn.example.test/logo.png");
        order.getPagamento().setNsu("123456");
        order.getPagamento().setAuthorizationCode("987654");
        order.getPagamento().setTransactionId("mp-777");
        User user = new User();
        user.setNome("Maria Souza");
        order.setUser(user);
        LoyaltyMovementRepository movements = mock(LoyaltyMovementRepository.class);
        LoyaltyMovement movement = new LoyaltyMovement();
        movement.setPointsDelta(15L);
        when(movements.findByIdempotencyKey("purchase:order-1284")).thenReturn(Optional.of(movement));

        ComprovanteDetalhesRequest details = ComprovanteCompraRequest.de(
                new ComprovanteDigitalService(mock(OrderService.class), movements).montar(order)).detalhes();

        assertThat(details.nsu()).isEqualTo("123456");
        assertThat(details.autorizacao()).isEqualTo("987654");
        assertThat(details.transacaoId()).isEqualTo("mp-777");
        assertThat(details.clienteVinculado()).isTrue();
        assertThat(details.clienteNome()).isEqualTo("Maria");
        assertThat(details.pontosGanhos()).isEqualTo(15L);
        assertThat(details.contato()).isEqualTo("(75) 3333-3333 - contato@empresa.test");
        assertThat(details.logoUrl()).isEqualTo("https://cdn.example.test/logo.png");
    }

    @Test
    void quantidadeSaiSemZerosENotacaoCientifica() {
        Order order = approvedOrder();
        OrderItem weighed = item("Queijo", "0.330", "10.000000", "3.300000", new Produto());
        OrderItem bulk = item("Água", "20.000", "1.000000", "20.000000", new Produto());
        order.setItems(List.of(weighed, bulk));
        order.setTotalCobrado(new BigDecimal("23.300000"));

        List<ItemComprovanteRequest> items = comprovante(order).itens();

        assertThat(items.get(0).quantidade().toString()).isEqualTo("0.33");
        assertThat(items.get(1).quantidade().toString()).isEqualTo("20");
    }

    @Test
    void jsonUsaOsNomesDoContratoPython() throws Exception {
        Order order = approvedOrder();
        order.getPagamento().setTransactionId("mp-777");

        ObjectMapper mapper = new ObjectMapper();
        JsonNode json = mapper.readTree(mapper.writeValueAsString(comprovante(order)));

        assertThat(json.at("/itens/0/quantidade").isIntegralNumber()).isTrue();
        assertThat(json.at("/itens/0/valor_unitario").asText()).isEqualTo("R$ 5,99");
        assertThat(json.at("/qr_data").asText()).isEmpty();
        assertThat(json.at("/detalhes/transacao_id").asText()).isEqualTo("mp-777");
        assertThat(json.at("/detalhes/cliente_vinculado").asBoolean()).isFalse();
        assertThat(json.at("/detalhes").has("logo_url")).isTrue();
    }

    @Test
    void formataMoedaEmPtBrSemConverterParaDouble() {
        assertThat(ComprovanteCompraRequest.formatCurrency(new BigDecimal("10")))
                .isEqualTo("R$ 10,00");
        assertThat(ComprovanteCompraRequest.formatCurrency(new BigDecimal("10.5")))
                .isEqualTo("R$ 10,50");
        assertThat(ComprovanteCompraRequest.formatCurrency(new BigDecimal("10.99")))
                .isEqualTo("R$ 10,99");
        assertThat(ComprovanteCompraRequest.formatCurrency(new BigDecimal("1234.56")))
                .isEqualTo("R$ 1.234,56");
    }

    @Test
    void paidAtAusenteGeraErroControlado() {
        Order order = approvedOrder();
        order.getPagamento().setPaidAt(null);

        assertThatThrownBy(() -> comprovante(order))
                .isInstanceOf(ApiBusinessException.class)
                .satisfies(error -> assertThat(((ApiBusinessException) error).getCode())
                        .isEqualTo("COMPROVANTE_INVALID_ORDER_DATA"))
                .hasMessageContaining("Pagamento aprovado sem data");
    }

    @Test
    void pedidoPendenteNaoPodeGerarComprovanteFinal() {
        Order order = approvedOrder();
        order.setStatus(OrderStatus.PENDING);

        assertThatThrownBy(() -> comprovante(order))
                .isInstanceOf(ApiBusinessException.class)
                .hasMessageContaining("pedido aprovado");
    }

    private ComprovanteCompraRequest comprovante(Order order) {
        return ComprovanteCompraRequest.de(new ComprovanteDigitalService(
                mock(OrderService.class), mock(LoyaltyMovementRepository.class)).montar(order));
    }

    private Order approvedOrder() {
        Empresa company = new Empresa();
        company.setNomeFantasia("Mercado Autônomo");
        company.setCnpj("12.345.678/0001-90");

        Endereco address = new Endereco();
        address.setRua("Rua das Flores");
        address.setNumero("123");
        address.setComplemento("Bloco B");
        address.setBairro("Centro");
        address.setCidade("Feira de Santana");
        address.setEstado("ba");

        Condominio condominium = new Condominio();
        condominium.setNome("Condomínio X");
        condominium.setEndereco(address);

        Terminal terminal = new Terminal();
        terminal.setCodigo("TERM-001");

        Produto cokeProduct = new Produto();
        cokeProduct.setPreco(new BigDecimal("5.99"));
        OrderItem coke = item("Coca-Cola 350ml", "2.000", "5.990000", "11.980000", cokeProduct);

        Produto waterProduct = new Produto();
        waterProduct.setPreco(new BigDecimal("3.50"));
        OrderItem water = item("Água", "1.000", "3.500000", "3.500000", waterProduct);

        Order order = new Order();
        order.setIdOrder("order-1284");
        order.setEmpresa(company);
        order.setCondominio(condominium);
        order.setTerminal(terminal);
        order.setItems(List.of(coke, water));
        order.setTotalCobrado(new BigDecimal("15.480000"));
        order.setStatus(OrderStatus.PROCESSED);

        PaymentAttempt payment = new PaymentAttempt();
        payment.setAttemptNumber(1);
        payment.setTipo(PagamentoTipo.PIX);
        payment.setStatus(PagamentoStatus.PROCESSED);
        payment.setPaidAt(Instant.parse("2026-09-12T18:40:00Z"));
        order.setPagamento(payment);
        return order;
    }

    private OrderItem item(
            String name,
            String quantity,
            String unitPrice,
            String subtotal,
            Produto product) {
        OrderItem item = new OrderItem();
        item.setNome(name);
        item.setQuantidade(new BigDecimal(quantity));
        item.setPrecoOriginal(new BigDecimal(unitPrice));
        item.setPrecoUnitarioAplicado(new BigDecimal(unitPrice));
        item.setSubtotalCalculado(new BigDecimal(subtotal));
        item.setProduto(product);
        return item;
    }
}
