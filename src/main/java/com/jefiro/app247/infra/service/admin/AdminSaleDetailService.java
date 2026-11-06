package com.jefiro.app247.infra.service.admin;

import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.OrderItem;
import com.jefiro.app247.domain.model.PaymentAttempt;
import com.jefiro.app247.domain.model.dto.admin.AdminSaleDetailResponse;
import com.jefiro.app247.domain.model.dto.admin.AdminSendReceiptRequest;
import com.jefiro.app247.domain.model.dto.comprovante.ComprovanteRequest;
import com.jefiro.app247.domain.model.enum_type.PagamentoStatus;
import com.jefiro.app247.domain.model.enum_type.PaymentMethodId;
import com.jefiro.app247.domain.model.enum_type.order.OrderStatus;
import com.jefiro.app247.infra.exception.ApiBusinessException;
import com.jefiro.app247.infra.repository.OrderRepository;
import com.jefiro.app247.infra.service.EmpresaContext;
import com.jefiro.app247.infra.service.MoneyPolicy;
import com.jefiro.app247.infra.service.comprovante.ComprovanteCompraRequest;
import com.jefiro.app247.infra.service.comprovante.ComprovanteEnvioResponse;
import com.jefiro.app247.infra.service.comprovante.ComprovanteService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class AdminSaleDetailService {
    private final OrderRepository orders;
    private final ComprovanteService comprovantes;

    public AdminSaleDetailService(OrderRepository orders, ComprovanteService comprovantes) {
        this.orders = orders;
        this.comprovantes = comprovantes;
    }

    @Transactional(readOnly = true)
    public AdminSaleDetailResponse detail(String orderId) {
        Order order = load(orderId);
        PaymentAttempt payment = order.getPagamento();
        boolean approved = approved(order);
        List<AdminSaleDetailResponse.Item> itens = order.getItems().stream()
                .map(AdminSaleDetailService::item).toList();
        return new AdminSaleDetailResponse(
                order.getIdOrder(),
                order.getStatus().name(),
                order.getCreatedAt(),
                order.getPaidAt() != null ? order.getPaidAt()
                        : payment != null ? payment.getPaidAt() : null,
                order.getCondominio() != null ? order.getCondominio().getNome() : null,
                order.getTerminal() != null ? order.getTerminal().getNome() : null,
                order.getTerminal() != null ? order.getTerminal().getCodigo() : null,
                itens,
                money(order.getSubtotal()),
                money(order.getDescontoPromocoes()),
                money(order.getDescontoCupom()),
                money(order.getTotalCobrado()),
                payment == null ? null : pagamento(payment),
                payment == null || payment.getRefundRequestedAt() == null ? null
                        : new AdminSaleDetailResponse.Reembolso(
                                payment.getRefundRequestedAt(), payment.getRefundConfirmedAt()),
                approved && payment.getProviderOrderId() != null
                        && !payment.getProviderOrderId().isBlank(),
                approved && payment.getPaidAt() != null
        );
    }

    @Transactional(readOnly = true)
    public ComprovanteEnvioResponse sendReceipt(String orderId, AdminSendReceiptRequest request) {
        Order order = load(orderId);
        if (!approved(order)) {
            throw new ApiBusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "COMPROVANTE_NOT_ALLOWED",
                    "O comprovante só pode ser enviado para venda aprovada");
        }
        String terminalId = order.getCarrinho() != null
                ? order.getCarrinho().getIdTerminal() : order.getIdTerminal();
        return comprovantes.enviar(new ComprovanteRequest(
                terminalId, order.getIdOrder(), request.tipoEnvio(), request.destinatario()));
    }

    private Order load(String orderId) {
        return orders.findAdminSaleDetail(orderId, EmpresaContext.require()).orElseThrow(() ->
                new ApiBusinessException(HttpStatus.NOT_FOUND, "PURCHASE_NOT_FOUND",
                        "Venda não encontrada"));
    }

    private static boolean approved(Order order) {
        PaymentAttempt payment = order.getPagamento();
        return order.getStatus() == OrderStatus.PROCESSED
                && payment != null && payment.getStatus() == PagamentoStatus.PROCESSED;
    }

    private static AdminSaleDetailResponse.Item item(OrderItem item) {
        BigDecimal valorFinal = MoneyPolicy.charged(item.getSubtotalCalculado());
        BigDecimal bruto = MoneyPolicy.charged(item.getPrecoOriginal().multiply(item.getQuantidade()));
        return new AdminSaleDetailResponse.Item(
                item.getCodigoInterno(),
                item.getNome(),
                item.getQuantidade(),
                item.getUnidadeMedida() == null ? null : item.getUnidadeMedida().name(),
                MoneyPolicy.charged(item.getPrecoOriginal()),
                bruto.subtract(valorFinal),
                valorFinal);
    }

    private static AdminSaleDetailResponse.Pagamento pagamento(PaymentAttempt payment) {
        PaymentMethodId method = payment.getPaymentMethodId();
        return new AdminSaleDetailResponse.Pagamento(
                payment.getStatus() == null ? null : payment.getStatus().name(),
                payment.getTipo() == null ? null : ComprovanteCompraRequest.paymentLabel(payment.getTipo()),
                method == null || method == PaymentMethodId.PIX ? null
                        : method.name().replaceFirst("^DEB", ""),
                payment.getInstallments(),
                payment.getTransactionId(),
                payment.getNsu(),
                payment.getAuthorizationCode());
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? null : MoneyPolicy.charged(value);
    }
}
