package com.jefiro.app247.domain.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderResponse(
        String id,

        String type,

        @JsonProperty("user_id")
        String userId,

        @JsonProperty("external_reference")
        String externalReference,

        String description,

        @JsonProperty("expiration_time")
        String expirationTime,

        @JsonProperty("processing_mode")
        String processingMode,

        @JsonProperty("country_code")
        String countryCode,

        @JsonProperty("integration_data")
        IntegrationData integrationData,

        String status,

        @JsonProperty("status_detail")
        String statusDetail,

        @JsonProperty("created_date")
        String createdDate,

        @JsonProperty("last_updated_date")
        String lastUpdatedDate,

        Integer version,

        Config config,

        Transactions transactions
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IntegrationData(
            @JsonProperty("application_id")
            String applicationId,

            @JsonProperty("platform_id")
            String platformId,

            @JsonProperty("integrator_id")
            String integratorId,

            Sponsor sponsor
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Sponsor(
            String id
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Config(
            Point point,

            @JsonProperty("payment_method")
            PaymentMethod paymentMethod
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Point(
            @JsonProperty("terminal_id")
            String terminalId,

            @JsonProperty("print_on_terminal")
            String printOnTerminal
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentMethod(
            @JsonProperty("default_type")
            String defaultType,

            @JsonProperty("default_installments")
            String defaultInstallments,

            @JsonProperty("installments_cost")
            String installmentsCost
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Transactions(
            List<Payment> payments
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payment(
            String id,

            String amount,

            String status,

            @JsonProperty("status_detail")
            String statusDetail,

            @JsonProperty("payment_method")
            PaymentTransactionMethod paymentMethod
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentTransactionMethod(
            String id,
            Integer installments,
            String type
    ) {
    }
}
