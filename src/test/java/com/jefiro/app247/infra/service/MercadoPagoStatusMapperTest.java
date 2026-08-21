package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.enum_type.PagamentoStatus;
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
}
