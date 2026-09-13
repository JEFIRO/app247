package com.jefiro.app247.infra.service.comprovante;

import com.jefiro.app247.domain.model.Condominio;
import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.OrderItem;
import com.jefiro.app247.domain.model.PaymentAttempt;
import com.jefiro.app247.domain.model.Produto;
import com.jefiro.app247.domain.model.auth.Endereco;
import com.jefiro.app247.domain.model.enum_type.PagamentoStatus;
import com.jefiro.app247.domain.model.enum_type.PagamentoTipo;
import com.jefiro.app247.domain.model.enum_type.order.OrderStatus;
import com.jefiro.app247.domain.model.terminal.Terminal;
import com.jefiro.app247.infra.exception.ApiBusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComprovanteCompraRequestTest {

    @Test
    void mapeiaSnapshotHistoricoCompletoNoContratoDaFastApi() {
        Order order = approvedOrder();

        ComprovanteCompraRequest receipt = new ComprovanteCompraRequest(order);

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
                new ItemComprovanteRequest("Coca-Cola 350ml", 2, "R$ 11,98"),
                new ItemComprovanteRequest("Água", 1, "R$ 3,50")
        );
    }

    @Test
    void usaSubtotalDoOrderItemMesmoQuandoPrecoAtualDoProdutoMudou() {
        Order order = approvedOrder();
        order.getItems().get(0).getProduto().setPreco(new BigDecimal("99.99"));

        ComprovanteCompraRequest receipt = new ComprovanteCompraRequest(order);

        assertThat(receipt.itens().get(0).valor()).isEqualTo("R$ 11,98");
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

        assertThatThrownBy(() -> new ComprovanteCompraRequest(order))
                .isInstanceOf(ApiBusinessException.class)
                .satisfies(error -> assertThat(((ApiBusinessException) error).getCode())
                        .isEqualTo("COMPROVANTE_INVALID_ORDER_DATA"))
                .hasMessageContaining("Pagamento aprovado sem data");
    }

    @Test
    void pedidoPendenteNaoPodeGerarComprovanteFinal() {
        Order order = approvedOrder();
        order.setStatus(OrderStatus.PENDING);

        assertThatThrownBy(() -> new ComprovanteCompraRequest(order))
                .isInstanceOf(ApiBusinessException.class)
                .hasMessageContaining("pedido aprovado");
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
        item.setPrecoUnitarioAplicado(new BigDecimal(unitPrice));
        item.setSubtotalCalculado(new BigDecimal(subtotal));
        item.setProduto(product);
        return item;
    }
}
