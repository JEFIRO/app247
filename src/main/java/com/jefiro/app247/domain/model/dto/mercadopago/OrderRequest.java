package com.jefiro.app247.domain.model.dto.mercadopago;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Corpo da requisição de criação de pedido no Mercado Pago
 * (POST /v1/orders).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderRequest(
        String type,

        @JsonProperty("external_reference")
        String externalReference,

        @JsonProperty("expiration_time")
        String expirationTime,

        String description,

        TransactionsRequest transactions,

        ConfigRequest config,

        @JsonProperty("integration_data")
        IntegrationDataRequest integrationData
) {

    /**
     * Construtor de conveniência sem "integration_data", para manter
     * compatível com a chamada atual em newOrder().
     */
    public OrderRequest(String type, String externalReference, String expirationTime,
                        String description, TransactionsRequest transactions, ConfigRequest config) {
        this(type, externalReference, expirationTime, description, transactions, config, null);
    }

    /**
     * Campo "transactions": lista de pagamentos do pedido.
     */
    public record TransactionsRequest(
            List<PaymentRequest> payments
    ) {
    }

    /**
     * Um pagamento dentro de "transactions.payments".
     */
    public record PaymentRequest(
            String amount
    ) {
    }

    /**
     * Campo "config": terminal Point e, opcionalmente, o método
     * de pagamento padrão.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ConfigRequest(
            PointRequest point,

            @JsonProperty("payment_method")
            PaymentMethodRequest paymentMethod
    ) {


        public ConfigRequest(PointRequest point) {
            this(point, null);
        }
    }

    /**
     * Campo "point" dentro de "config": terminal usado e modo de
     * impressão do comprovante.
     */
    public record PointRequest(
            @JsonProperty("terminal_id")
            String terminalId,

            @JsonProperty("print_on_terminal")
            String printOnTerminal
    ) {
    }


    public record PaymentMethodRequest(
            @JsonProperty("default_type")
            String defaultType,

            @JsonProperty("default_installments")
            Integer defaultInstallments,

            @JsonProperty("installments_cost")
            String installmentsCost
    ) {
    }


    public record IntegrationDataRequest(
            @JsonProperty("platform_id")
            String platformId,

            @JsonProperty("integrator_id")
            String integratorId,

            SponsorRequest sponsor
    ) {
    }

    public record SponsorRequest(
            String id
    ) {
    }
}