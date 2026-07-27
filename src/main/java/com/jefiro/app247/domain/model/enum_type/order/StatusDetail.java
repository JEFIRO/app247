package com.jefiro.app247.domain.model.enum_type.order;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum StatusDetail {

    CREATED("created"),                                   // A order foi criada. Detalhe espelha o status "created" (sem motivo específico).
    AT_TERMINAL("at_terminal"),                           // A order está no terminal. Detalhe espelha o status "at_terminal".
    ACCREDITED("accredited"),                             // O pagamento foi acreditado com sucesso. Único valor permitido para o status "processed".
    CANCELED("canceled"),                                 // A order foi cancelada. Detalhe espelha o status "canceled".
    EXPIRED("expired"),                                   // A order expirou. Detalhe espelha o status "expired".
    ACTION_REQUIRED("action_required"),                   // Ação requerida do cliente. Detalhe espelha o status "action_required".
    REFUNDED("refunded"),                                 // O pagamento foi reembolsado. Detalhe espelha o status "refunded".
    BAD_FILLED_CARD_DATA("bad_filled_card_data"),         // Os dados do cartão estão incorretos. Disponível apenas para o status "failed".
    REQUIRED_CALL_FOR_AUTHORIZE("required_call_for_authorize"), // É necessária uma ligação para autorizar o pagamento. Disponível apenas para o status "failed".
    CARD_DISABLED("card_disabled"),                       // O cartão está desabilitado. Disponível apenas para o status "failed".
    HIGH_RISK("high_risk"),                               // O pagamento foi rejeitado por alto risco. Disponível apenas para o status "failed".
    INSUFFICIENT_AMOUNT("insufficient_amount"),           // A conta possui fundos insuficientes. Disponível apenas para o status "failed".
    INVALID_INSTALLMENTS("invalid_installments"),         // O número de parcelas é inválido. Disponível apenas para o status "failed".
    MAX_ATTEMPTS_EXCEEDED("max_attempts_exceeded"),       // O número máximo de tentativas foi excedido. Disponível apenas para o status "failed".
    REJECTED_OTHER_REASON("rejected_other_reason"),       // O pagamento foi rejeitado por outro motivo. Disponível apenas para o status "failed".
    PROCESSING_ERROR("processing_error");                 // Ocorreu um erro ao processar o pagamento. Disponível apenas para o status "failed".

    private final String value;

    StatusDetail(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static StatusDetail fromValue(String value) {
        for (StatusDetail detail : values()) {
            if (detail.value.equals(value)) {
                return detail;
            }
        }
        throw new IllegalArgumentException("Valor de status_detail desconhecido: " + value);
    }
}