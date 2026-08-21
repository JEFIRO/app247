package com.jefiro.app247.domain.model.enum_type.order;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum OrderStatus {

    PENDING("pending"),                 // Status local, antes de qualquer resposta do Mercado Pago.
    CREATED("created"),                 // A order foi criada no Mercado Pago, aguardando ir ao terminal.
    AT_TERMINAL("at_terminal"),         // A order está no terminal Point, aguardando o cliente interagir.
    PROCESSED("processed"),             // A order foi processada com sucesso.
    CANCELED("canceled"),               // A order foi cancelada.
    EXPIRED("expired"),                 // A order expirou.
    FAILED("failed"),                   // O pagamento falhou.
    REFUNDED("refunded"),               // O pagamento foi reembolsado.
    ACTION_REQUIRED("action_required"); // Ação requerida do cliente.

    private final String value;

    OrderStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static OrderStatus fromValue(String value) {
        for (OrderStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Valor de status desconhecido: " + value);
    }

    public static OrderStatus findByValue(String value) {
        if (value == null) {
            return null;
        }
        for (OrderStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        return null;
    }

    public boolean canTransitionTo(OrderStatus next) {
        if (next == null || this == next) {
            return false;
        }
        return switch (this) {
            case PENDING -> true;
            case CREATED -> next != PENDING;
            case AT_TERMINAL -> next != PENDING && next != CREATED;
            case ACTION_REQUIRED -> next == PROCESSED || next == FAILED
                    || next == CANCELED || next == EXPIRED;
            case PROCESSED -> next == REFUNDED;
            case CANCELED, EXPIRED, FAILED, REFUNDED -> false;
        };
    }
}
