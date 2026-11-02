package com.jefiro.app247.infra.service.comprovante;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jefiro.app247.domain.model.Condominio;
import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.PaymentAttempt;
import com.jefiro.app247.domain.model.auth.Endereco;
import com.jefiro.app247.domain.model.dto.comprovante.ComprovanteDigitalResponse;
import com.jefiro.app247.domain.model.dto.comprovante.ComprovanteDigitalResponse.Estabelecimento;
import com.jefiro.app247.domain.model.dto.comprovante.ComprovanteDigitalResponse.Fidelidade;
import com.jefiro.app247.domain.model.dto.comprovante.ComprovanteDigitalResponse.Pagamento;
import com.jefiro.app247.domain.model.enum_type.PagamentoStatus;
import com.jefiro.app247.domain.model.enum_type.PagamentoTipo;
import com.jefiro.app247.domain.model.enum_type.order.OrderStatus;
import com.jefiro.app247.infra.exception.ApiBusinessException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
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
        @JsonProperty("qr_data") String qrData,
        ComprovanteDetalhesRequest detalhes
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

    /** Projeta os dados estruturados no contrato de apresentação do serviço Python. */
    static ComprovanteCompraRequest de(ComprovanteDigitalResponse comprovante) {
        Estabelecimento estabelecimento = comprovante.estabelecimento();
        Pagamento pagamento = comprovante.pagamento();
        Fidelidade fidelidade = comprovante.fidelidade();
        return new ComprovanteCompraRequest(
                estabelecimento.nome(),
                estabelecimento.cnpj(),
                estabelecimento.endereco(),
                estabelecimento.local(),
                comprovante.numeroCompra(),
                comprovante.data(),
                comprovante.terminal().codigo(),
                comprovante.hora(),
                comprovante.itens().stream().map(ComprovanteCompraRequest::mapItem).toList(),
                formatCurrency(comprovante.total()),
                pagamento.forma(),
                pagamento.status(),
                "",
                new ComprovanteDetalhesRequest(
                        formatCurrency(comprovante.subtotal()),
                        comprovante.descontos().stream()
                                .map(desconto -> new DescontoComprovanteRequest(
                                        desconto.descricao(), formatCurrency(desconto.valor())))
                                .toList(),
                        comprovante.arredondamento().signum() == 0
                                ? null : formatCurrency(comprovante.arredondamento()),
                        pagamento.bandeira(),
                        pagamento.parcelas(),
                        pagamento.transacaoId(),
                        pagamento.nsu(),
                        pagamento.autorizacao(),
                        comprovante.cliente().vinculado(),
                        comprovante.cliente().primeiroNome(),
                        fidelidade == null ? null : fidelidade.pontosGanhos(),
                        fidelidade == null || fidelidade.cashbackGanho() == null
                                ? null : formatCurrency(fidelidade.cashbackGanho()),
                        contato(estabelecimento),
                        estabelecimento.logoUrl()
                )
        );
    }

    /** Regras comuns a todo comprovante: venda aprovada e dados históricos completos. */
    static void validar(Order order) {
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
    }

    // `valor` usa o preço original: os descontos aparecem em linhas próprias e a conta fecha.
    private static ItemComprovanteRequest mapItem(ComprovanteDigitalResponse.Item item) {
        return new ItemComprovanteRequest(
                item.descricao(),
                quantidade(item.quantidade()),
                formatCurrency(item.subtotal()),
                formatCurrency(item.valorUnitario())
        );
    }

    /** Evita notação científica e casas vazias, mantendo inteiros como inteiros no JSON. */
    private static BigDecimal quantidade(BigDecimal value) {
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.scale() < 0 ? normalized.setScale(0) : normalized;
    }

    private static String contato(Estabelecimento estabelecimento) {
        return Stream.of(estabelecimento.telefone(), estabelecimento.email())
                .filter(ComprovanteCompraRequest::notBlank)
                .reduce((left, right) -> left + " - " + right)
                .orElse(null);
    }

    static String formatCurrency(BigDecimal value) {
        require(value != null, "Valor monetário não informado");
        return CURRENCY_FORMAT.get().format(value)
                .replace('\u00A0', ' ')
                .replace('\u202F', ' ');
    }

    static String formatAddress(Endereco address) {
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

    static String formatLocation(Condominio condominio, Endereco address) {
        require(notBlank(condominio.getNome()), "Condomínio sem nome");
        require(notBlank(address.getCidade()), "Endereço do condomínio sem cidade");
        require(notBlank(address.getEstado()), "Endereço do condomínio sem estado");
        return condominio.getNome().trim() + " - "
                + address.getCidade().trim() + "/" + address.getEstado().trim().toUpperCase(PT_BR);
    }

    static String paymentLabel(PagamentoTipo type) {
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
}
