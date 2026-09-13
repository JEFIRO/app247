package com.jefiro.app247.infra.service.comprovante;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jefiro.app247.domain.model.Condominio;
import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.OrderItem;
import com.jefiro.app247.domain.model.PaymentAttempt;
import com.jefiro.app247.domain.model.auth.Endereco;
import com.jefiro.app247.domain.model.enum_type.PagamentoStatus;
import com.jefiro.app247.domain.model.enum_type.PagamentoTipo;
import com.jefiro.app247.domain.model.enum_type.order.OrderStatus;
import com.jefiro.app247.infra.exception.ApiBusinessException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

/** Snapshot serializável enviado ao serviço Python de comprovantes. */
public record ComprovanteCompraRequest(
        String empresa,
        String cnpj,
        String endereco,
        String local,
        String pedido,
        String data,
        String terminal,
        String hora,
        List<ItemComprovanteRequest> itens,
        String total,
        String pagamento,
        String status,
        @JsonProperty("qr_data") String qrData
) {
    static final ZoneId RECEIPT_ZONE = ZoneId.of("America/Bahia");
    static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final ThreadLocal<NumberFormat> CURRENCY_FORMAT = ThreadLocal.withInitial(() -> {
        NumberFormat formatter = NumberFormat.getCurrencyInstance(PT_BR);
        formatter.setCurrency(Currency.getInstance("BRL"));
        formatter.setMinimumFractionDigits(2);
        formatter.setMaximumFractionDigits(2);
        formatter.setRoundingMode(RoundingMode.HALF_EVEN);
        return formatter;
    });

    public ComprovanteCompraRequest(Order order) {
        this(from(order));
    }

    private ComprovanteCompraRequest(ReceiptFields fields) {
        this(
                fields.empresa,
                fields.cnpj,
                fields.endereco,
                fields.local,
                fields.pedido,
                fields.data,
                fields.terminal,
                fields.hora,
                fields.itens,
                fields.total,
                fields.pagamento,
                "APROVADO",
                ""
        );
    }

    private static ReceiptFields from(Order order) {
        require(order != null, "Pedido não informado");
        require(order.getStatus() == OrderStatus.PROCESSED,
                "O comprovante só pode ser enviado para pedido aprovado");

        PaymentAttempt payment = order.getPagamento();
        require(payment != null && payment.getStatus() == PagamentoStatus.PROCESSED,
                "O comprovante só pode ser enviado para pagamento aprovado");
        require(payment.getPaidAt() != null,
                "Pagamento aprovado sem data de aprovação");

        require(order.getEmpresa() != null, "Pedido sem empresa");
        require(order.getCondominio() != null, "Pedido sem condomínio");
        require(order.getCondominio().getEndereco() != null, "Condomínio sem endereço");
        require(order.getTerminal() != null, "Pedido sem terminal");
        require(notBlank(order.getIdOrder()), "Pedido sem identificador");
        require(notBlank(order.getEmpresa().getNomeFantasia()), "Empresa sem nome fantasia");
        require(notBlank(order.getEmpresa().getCnpj()), "Empresa sem CNPJ");
        require(notBlank(order.getTerminal().getCodigo()), "Terminal sem código");
        require(order.getItems() != null && !order.getItems().isEmpty(), "Pedido sem itens");
        require(order.getTotalCobrado() != null, "Pedido sem total cobrado");
        require(payment.getTipo() != null, "Pagamento sem meio de pagamento");

        Condominio condominio = order.getCondominio();
        Endereco address = condominio.getEndereco();
        Instant paidAt = payment.getPaidAt();

        List<ItemComprovanteRequest> items = order.getItems().stream()
                .map(ComprovanteCompraRequest::mapItem)
                .toList();

        return new ReceiptFields(
                order.getEmpresa().getNomeFantasia(),
                order.getEmpresa().getCnpj(),
                formatAddress(address),
                formatLocation(condominio, address),
                order.getIdOrder(),
                paidAt.atZone(RECEIPT_ZONE).format(DATE_FORMAT),
                order.getTerminal().getCodigo(),
                paidAt.atZone(RECEIPT_ZONE).format(TIME_FORMAT),
                items,
                formatCurrency(order.getTotalCobrado()),
                paymentLabel(payment.getTipo())
        );
    }

    private static ItemComprovanteRequest mapItem(OrderItem item) {
        require(item != null, "Pedido possui item inválido");
        require(notBlank(item.getNome()), "Item do pedido sem nome");
        require(item.getQuantidade() != null, "Item do pedido sem quantidade");
        require(item.getSubtotalCalculado() != null, "Item do pedido sem subtotal histórico");

        final int quantity;
        try {
            quantity = item.getQuantidade().intValueExact();
        } catch (ArithmeticException exception) {
            throw invalidData("Quantidade não inteira não é suportada pelo contrato do comprovante");
        }
        require(quantity > 0, "Item do pedido com quantidade inválida");

        // O contrato Python exibe `valor` na coluna da linha; portanto usamos o
        // subtotal histórico do OrderItem, nunca o preço atual de Produto.
        return new ItemComprovanteRequest(
                item.getNome(),
                quantity,
                formatCurrency(item.getSubtotalCalculado())
        );
    }

    static String formatCurrency(BigDecimal value) {
        require(value != null, "Valor monetário não informado");
        return CURRENCY_FORMAT.get().format(value)
                .replace('\u00A0', ' ')
                .replace('\u202F', ' ');
    }

    private static String formatAddress(Endereco address) {
        String street = Stream.of(address.getRua(), address.getNumero())
                .filter(ComprovanteCompraRequest::notBlank)
                .map(String::trim)
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
        String formatted = Stream.of(street, address.getComplemento(), address.getBairro())
                .filter(ComprovanteCompraRequest::notBlank)
                .map(String::trim)
                .reduce((left, right) -> left + " - " + right)
                .orElse("");
        require(notBlank(formatted), "Endereço do condomínio incompleto");
        return formatted;
    }

    private static String formatLocation(Condominio condominio, Endereco address) {
        require(notBlank(condominio.getNome()), "Condomínio sem nome");
        require(notBlank(address.getCidade()), "Endereço do condomínio sem cidade");
        require(notBlank(address.getEstado()), "Endereço do condomínio sem estado");
        return condominio.getNome().trim() + " - "
                + address.getCidade().trim() + "/" + address.getEstado().trim().toUpperCase(PT_BR);
    }

    private static String paymentLabel(PagamentoTipo type) {
        return switch (Objects.requireNonNull(type)) {
            case PIX -> "PIX";
            case CREDIT_CARD -> "CRÉDITO";
            case DEBIT_CARD -> "DÉBITO";
            case ALIMENT_CARD -> "CARTÃO ALIMENTAÇÃO";
        };
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw invalidData(message);
        }
    }

    private static ApiBusinessException invalidData(String message) {
        return new ApiBusinessException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "COMPROVANTE_INVALID_ORDER_DATA",
                message
        );
    }

    private record ReceiptFields(
            String empresa,
            String cnpj,
            String endereco,
            String local,
            String pedido,
            String data,
            String terminal,
            String hora,
            List<ItemComprovanteRequest> itens,
            String total,
            String pagamento
    ) {
    }
}
