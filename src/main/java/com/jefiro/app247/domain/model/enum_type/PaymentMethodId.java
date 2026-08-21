package com.jefiro.app247.domain.model.enum_type;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PaymentMethodId {

    AMEX("amex"),
    MASTER("master"),
    VISA("visa"),
    DEBMASTER("debmaster"),
    DEBVISA("debvisa"),
    ELO("elo"),
    DINERS("diners"),
    HIPERCARD("hipercard"),
    PIX("account_money");

    private final String value;

    PaymentMethodId(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static PaymentMethodId fromValue(String value) {
        for (PaymentMethodId method : values()) {
            if (method.value.equalsIgnoreCase(value)) {
                return method;
            }
        }

        throw new IllegalArgumentException(
            "Método de pagamento inválido: " + value
        );
    }

    public static PaymentMethodId findByValue(String value) {
        if (value == null) {
            return null;
        }
        for (PaymentMethodId method : values()) {
            if (method.value.equalsIgnoreCase(value)) {
                return method;
            }
        }
        return null;
    }
}
