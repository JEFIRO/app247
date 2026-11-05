package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.enum_type.PagamentoStatus;
import com.jefiro.app247.domain.model.enum_type.PagamentoTipo;
import com.jefiro.app247.domain.model.enum_type.PaymentMethodId;
import com.jefiro.app247.domain.model.enum_type.TerminalPaymentStatus;
import com.jefiro.app247.domain.model.enum_type.order.OrderStatus;
import com.jefiro.app247.domain.model.mapper.MercadoPagoStatusMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class MercadoPagoStatusMapperTest {
    @ParameterizedTest
    @CsvSource({
            "PENDING,PENDING,WAITING_PAYMENT",
            "CREATED,PENDING,WAITING_PAYMENT",
            "AT_TERMINAL,PENDING,WAITING_PAYMENT",
            "ACTION_REQUIRED,ACTION_REQUIRED,ACTION_REQUIRED",
            "PROCESSED,PROCESSED,APPROVED",
            "FAILED,FAILED,REJECTED",
            "CANCELED,CANCELED,CANCELLED",
            "EXPIRED,EXPIRED,EXPIRED",
            "REFUNDED,REFUNDED,REFUNDED"
    })
    void centralizaStatusDoDominioEDoTerminal(
            OrderStatus orderStatus,
            PagamentoStatus pagamentoStatus,
            TerminalPaymentStatus terminalStatus
    ) {
        assertThat(MercadoPagoStatusMapper.toPagamentoStatus(orderStatus)).isEqualTo(pagamentoStatus);
        assertThat(MercadoPagoStatusMapper.toTerminalStatus(orderStatus)).isEqualTo(terminalStatus);
    }

    @ParameterizedTest
    @CsvSource({
            "credit_card,CREDIT_CARD",
            "debit_card,DEBIT_CARD",
            "qr,PIX",
            "bank_transfer,PIX",
            "voucher_card,ALIMENT_CARD"
    })
    void mapeiaTipoDoMeioDePagamento(String type, PagamentoTipo tipo) {
        assertThat(MercadoPagoStatusMapper.toPagamentoTipo(type)).isEqualTo(tipo);
    }

    @ParameterizedTest
    @CsvSource({"pix,PIX", "account_money,PIX", "master,MASTER", "debvisa,DEBVISA"})
    void reconheceIdDoMeioDePagamento(String id, PaymentMethodId method) {
        assertThat(PaymentMethodId.findByValue(id)).isEqualTo(method);
    }
}
