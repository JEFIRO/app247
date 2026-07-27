package com.jefiro.app247.domain.model.enum_type.order;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum WebhookAction {

    ORDER_CREATED("order.created"),               // A order foi criada.
    ORDER_PROCESSED("order.processed"),           // A order foi processada com sucesso.
    ORDER_CANCELED("order.canceled"),             // A order foi cancelada.
    ORDER_EXPIRED("order.expired"),                 // A order expirou.
    ORDER_FAILED("order.failed"),                   // O pagamento da order falhou.
    ORDER_REFUNDED("order.refunded"),               // O pagamento da order foi reembolsado.
    ORDER_ACTION_REQUIRED("order.action_required"); // Ação requerida do cliente na order.

    private final String value;

    WebhookAction(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static WebhookAction fromValue(String value) {
        for (WebhookAction action : values()) {
            if (action.value.equals(value)) {
                return action;
            }
        }
        throw new IllegalArgumentException("Valor de action desconhecido: " + value);
    }
}