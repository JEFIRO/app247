package com.jefiro.app247.domain.model.dto;

import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.enum_type.PagamentoStatus;
import com.jefiro.app247.domain.model.enum_type.PagamentoTipo;
import com.jefiro.app247.domain.model.enum_type.order.OrderStatus;
import com.jefiro.app247.domain.model.dto.response.CarrinhoResponseDTO;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderDetailResponse(
        String orderId,
        OrderStatus status,
        BigDecimal subtotal,
        BigDecimal desconto,
        BigDecimal total,
        BigDecimal totalCalculado,
        BigDecimal totalCobrado,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime paidAt,
        String mercadoPagoOrderId,
        Integer mercadoPagoEventVersion,
        CarrinhoResponseDTO carrinho,
        PaymentSummary pagamento
) {
    public OrderDetailResponse(Order order) {
        this(order.getIdOrder(), order.getStatus(), order.getSubtotal(), order.getDesconto(), order.getTotal(),
                order.getTotalCalculado(), order.getTotalCobrado(),
                order.getCreatedAt(), order.getUpdatedAt(), order.getPaidAt(), order.getMpOrderId(),
                order.getMpEventVersion(), order.getCarrinho() != null ? new CarrinhoResponseDTO(order.getCarrinho()) : null,
                order.getPagamento() != null ? new PaymentSummary(
                        order.getPagamento().getIdPagamento(), order.getPagamento().getStatus(),
                        order.getPagamento().getTipo(), order.getPagamento().getTransactionId(),
                        order.getPagamento().getStatusDetail()) : null);
    }

    public record PaymentSummary(
            String pagamentoId,
            PagamentoStatus status,
            PagamentoTipo tipo,
            String transactionId,
            String statusDetail
    ) {}
}
