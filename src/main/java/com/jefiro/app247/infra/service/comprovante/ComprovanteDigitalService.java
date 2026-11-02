package com.jefiro.app247.infra.service.comprovante;

import com.jefiro.app247.domain.model.Condominio;
import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.OrderItem;
import com.jefiro.app247.domain.model.PaymentAttempt;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.dto.comprovante.ComprovanteDigitalResponse;
import com.jefiro.app247.domain.model.dto.comprovante.ComprovanteDigitalResponse.Cliente;
import com.jefiro.app247.domain.model.dto.comprovante.ComprovanteDigitalResponse.Desconto;
import com.jefiro.app247.domain.model.dto.comprovante.ComprovanteDigitalResponse.Estabelecimento;
import com.jefiro.app247.domain.model.dto.comprovante.ComprovanteDigitalResponse.Fidelidade;
import com.jefiro.app247.domain.model.dto.comprovante.ComprovanteDigitalResponse.Item;
import com.jefiro.app247.domain.model.dto.comprovante.ComprovanteDigitalResponse.Pagamento;
import com.jefiro.app247.domain.model.dto.comprovante.ComprovanteDigitalResponse.TerminalInfo;
import com.jefiro.app247.domain.model.enum_type.PaymentMethodId;
import com.jefiro.app247.infra.repository.LoyaltyMovementRepository;
import com.jefiro.app247.infra.service.MoneyPolicy;
import com.jefiro.app247.infra.service.OrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Monta o comprovante estruturado somente com o snapshot histórico da compra. */
@Service
public class ComprovanteDigitalService {
    static final String AVISO_NAO_FISCAL = "Este comprovante não é documento fiscal.";

    private final OrderService orderService;
    private final LoyaltyMovementRepository movements;

    public ComprovanteDigitalService(OrderService orderService, LoyaltyMovementRepository movements) {
        this.orderService = orderService;
        this.movements = movements;
    }

    @Transactional(readOnly = true)
    public ComprovanteDigitalResponse paraTerminal(String orderId, String terminalId) {
        return montar(orderService.getOrderForTerminal(orderId, terminalId));
    }

    /** A Order já deve ter sido carregada com a checagem de ownership do chamador. */
    @Transactional(readOnly = true)
    public ComprovanteDigitalResponse montar(Order order) {
        ComprovanteCompraRequest.validar(order);

        PaymentAttempt payment = order.getPagamento();
        Instant paidAt = payment.getPaidAt();
        List<Item> itens = order.getItems().stream().map(ComprovanteDigitalService::item).toList();
        BigDecimal subtotal = somar(itens.stream().map(Item::subtotal).toList());
        List<Desconto> descontos = descontos(order, itens);
        BigDecimal descontoTotal = somar(descontos.stream().map(Desconto::valor).toList());
        BigDecimal total = MoneyPolicy.charged(order.getTotalCobrado());

        return new ComprovanteDigitalResponse(
                estabelecimento(order),
                order.getIdOrder(),
                paidAt,
                paidAt.atZone(ComprovanteCompraRequest.RECEIPT_ZONE).format(ComprovanteCompraRequest.DATE_FORMAT),
                paidAt.atZone(ComprovanteCompraRequest.RECEIPT_ZONE).format(ComprovanteCompraRequest.TIME_FORMAT),
                new TerminalInfo(order.getTerminal().getCodigo(), order.getTerminal().getNome()),
                itens,
                subtotal,
                descontos,
                descontoTotal,
                // Valores por linha são arredondados: a diferença de centavos fica explícita.
                total.subtract(subtotal.subtract(descontoTotal)),
                total,
                pagamento(payment),
                cliente(order),
                fidelidade(order),
                null,
                AVISO_NAO_FISCAL
        );
    }

    private static Estabelecimento estabelecimento(Order order) {
        Empresa empresa = order.getEmpresa();
        Condominio condominio = order.getCondominio();
        return new Estabelecimento(
                empresa.getNomeFantasia(),
                textoOuNulo(empresa.getRazaoSocial()),
                empresa.getCnpj(),
                ComprovanteCompraRequest.formatAddress(condominio.getEndereco()),
                ComprovanteCompraRequest.formatLocation(condominio, condominio.getEndereco()),
                textoOuNulo(empresa.getTelefone()),
                textoOuNulo(empresa.getEmail()),
                textoOuNulo(empresa.getLogoUrl()),
                textoOuNulo(empresa.getLogoDarkUrl())
        );
    }

    private static Item item(OrderItem item) {
        BigDecimal subtotal = MoneyPolicy.charged(item.getPrecoOriginal().multiply(item.getQuantidade()));
        BigDecimal valorFinal = MoneyPolicy.charged(item.getSubtotalCalculado());
        return new Item(
                item.getCodigoInterno(),
                item.getNome(),
                item.getQuantidade(),
                item.getUnidadeMedida() == null ? null : item.getUnidadeMedida().name(),
                MoneyPolicy.charged(item.getPrecoOriginal()),
                subtotal,
                subtotal.subtract(valorFinal),
                valorFinal
        );
    }

    private static List<Desconto> descontos(Order order, List<Item> itens) {
        List<Desconto> descontos = new ArrayList<>();
        BigDecimal promocoes = somar(itens.stream().map(Item::desconto).toList());
        if (promocoes.signum() > 0) {
            descontos.add(new Desconto("PROMOCAO", "Promoções", promocoes));
        }
        BigDecimal cupom = order.getDescontoCupom() == null
                ? BigDecimal.ZERO : MoneyPolicy.charged(order.getDescontoCupom());
        if (cupom.signum() > 0) {
            String nome = order.getCoupon() == null ? "Cupom" : "Cupom " + order.getCoupon().getNome();
            descontos.add(new Desconto("CUPOM", nome, cupom));
        }
        return List.copyOf(descontos);
    }

    private static Pagamento pagamento(PaymentAttempt payment) {
        return new Pagamento(
                ComprovanteCompraRequest.paymentLabel(payment.getTipo()),
                bandeira(payment.getPaymentMethodId()),
                payment.getInstallments(),
                "APROVADO",
                textoOuNulo(payment.getTransactionId()),
                textoOuNulo(payment.getNsu()),
                textoOuNulo(payment.getAuthorizationCode())
        );
    }

    private static String bandeira(PaymentMethodId method) {
        if (method == null || method == PaymentMethodId.PIX) return null;
        return method.name().replaceFirst("^DEB", "");
    }

    /** Só expõe o primeiro nome: o comprovante pode ser enviado a outro destinatário. */
    private static Cliente cliente(Order order) {
        User user = order.getUser();
        if (user == null && order.getCustomerIdentity() != null) {
            user = order.getCustomerIdentity().getUser();
        }
        if (user == null) return new Cliente(false, null);
        String nome = textoOuNulo(user.getNome());
        return new Cliente(true, nome == null ? null : nome.trim().split("\\s+")[0]);
    }

    private Fidelidade fidelidade(Order order) {
        return movements.findByIdempotencyKey("purchase:" + order.getIdOrder())
                .map(movement -> new Fidelidade(
                        movement.getPointsDelta(),
                        movement.getCashbackDelta() == null
                                ? null : MoneyPolicy.charged(movement.getCashbackDelta())))
                .orElse(null);
    }

    private static BigDecimal somar(List<BigDecimal> valores) {
        return MoneyPolicy.charged(valores.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static String textoOuNulo(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
