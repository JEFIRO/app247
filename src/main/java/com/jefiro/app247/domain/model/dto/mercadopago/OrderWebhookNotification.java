package com.jefiro.app247.domain.model.dto.mercadopago;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderWebhookNotification(
        String action,

        @JsonProperty("api_version")
        String apiVersion,

        @JsonProperty("application_id")
        String applicationId,

        Data data,

        @JsonProperty("date_created")
        String dateCreated,

        @JsonProperty("live_mode")
        boolean liveMode,

        String type,

        @JsonProperty("user_id")
        String userId
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(
            @JsonProperty("external_reference")
            String externalReference,

            String id,

            String status,

            @JsonProperty("status_detail")
            String statusDetail,

            @JsonProperty("total_paid_amount")
            String totalPaidAmount,

            Transactions transactions,

            String type,

            Integer version
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Transactions(
            List<Payment> payments
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payment(
            String amount,

            String id,

            @JsonProperty("paid_amount")
            String paidAmount,

            @JsonProperty("payment_method")
            PaymentMethod paymentMethod,

            Reference reference,

            String status,

            @JsonProperty("status_detail")
            String statusDetail
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentMethod(
            String id,

            Integer installments,

            String type
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Reference(
            String id
    ) {
    }
}
