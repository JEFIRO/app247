package com.jefiro.app247.domain.model.mapper;

import com.jefiro.app247.domain.model.enum_type.PagamentoStatus;
import com.jefiro.app247.domain.model.enum_type.PagamentoTipo;
import com.jefiro.app247.domain.model.enum_type.TerminalPaymentStatus;
import com.jefiro.app247.domain.model.enum_type.order.OrderStatus;

public final class MercadoPagoStatusMapper {
    private MercadoPagoStatusMapper() {
    }

    public static PagamentoStatus toPagamentoStatus(OrderStatus status) {
        return switch (status) {
            case PROCESSED -> PagamentoStatus.PROCESSED;
            case CANCELED -> PagamentoStatus.CANCELED;
            case EXPIRED -> PagamentoStatus.EXPIRED;
            case FAILED -> PagamentoStatus.FAILED;
            case REFUNDED -> PagamentoStatus.REFUNDED;
            case ACTION_REQUIRED -> PagamentoStatus.ACTION_REQUIRED;
            case PENDING, CREATED, AT_TERMINAL -> PagamentoStatus.PENDING;
        };
    }

    public static TerminalPaymentStatus toTerminalStatus(OrderStatus status) {
        if (status == null) return TerminalPaymentStatus.WAITING_PAYMENT;
        return switch (status) {
            case PENDING, CREATED, AT_TERMINAL -> TerminalPaymentStatus.WAITING_PAYMENT;
            case PROCESSED -> TerminalPaymentStatus.APPROVED;
            case FAILED -> TerminalPaymentStatus.REJECTED;
            case CANCELED -> TerminalPaymentStatus.CANCELLED;
            case EXPIRED -> TerminalPaymentStatus.EXPIRED;
            case ACTION_REQUIRED -> TerminalPaymentStatus.ACTION_REQUIRED;
            case REFUNDED -> TerminalPaymentStatus.REFUNDED;
        };
    }

    public static PagamentoTipo toPagamentoTipo(String type) {
        if (type == null) return null;
        return switch (type) {
            case "credit_card" -> PagamentoTipo.CREDIT_CARD;
            case "debit_card" -> PagamentoTipo.DEBIT_CARD;
            case "qr" -> PagamentoTipo.PIX;
            case "voucher_card" -> PagamentoTipo.ALIMENT_CARD;
            default -> null;
        };
    }

    public static String message(TerminalPaymentStatus status) {
        return switch (status) {
            case WAITING_PAYMENT -> "Cobrança enviada para a maquininha; pressione o botão verde e siga as instruções";
            case APPROVED -> "Pagamento aprovado";
            case REJECTED -> "Pagamento recusado";
            case CANCELLED -> "Pagamento cancelado";
            case EXPIRED -> "Cobrança expirada";
            case ACTION_REQUIRED -> "Ação necessária na maquininha";
            case REFUNDED -> "Pagamento reembolsado";
        };
    }
}
