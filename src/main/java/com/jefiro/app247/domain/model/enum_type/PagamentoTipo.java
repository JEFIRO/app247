package com.jefiro.app247.domain.model.enum_type;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PagamentoTipo {

    PIX("account_money"),
    CREDIT_CARD("credit_card"),
    DEBIT_CARD("debit_card"),
    ALIMENT_CARD("voucher_card");

    private final String value;

    PagamentoTipo(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static PagamentoTipo fromValue(String value) {
        for (PagamentoTipo tipo : values()) {
            if (tipo.value.equalsIgnoreCase(value)) {
                return tipo;
            }
        }

        throw new IllegalArgumentException(
                "PagamentoTipo inválido: " + value
        );
    }

    public static PagamentoTipo findByValue(String value) {
        if (value == null) {
            return null;
        }
        for (PagamentoTipo tipo : values()) {
            if (tipo.value.equalsIgnoreCase(value)) {
                return tipo;
            }
        }
        return null;
    }
}
