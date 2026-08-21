package com.jefiro.app247.infra.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class TestarWebhookMPTest {

    private final TestarWebhookMP service = new TestarWebhookMP(
            new RestTemplate(), null, "token-de-teste", "NEWLAND_N950__SBX0000001"
    );

    @Test
    void simulacaoDeCreditoCombinaCreditCardComVisaEParcelas() {
        assertThat(service.payloadCenario("aprovado"))
                .contains("\"payment_method_type\": \"credit_card\"")
                .contains("\"payment_method_id\": \"visa\"")
                .contains("\"installments\": 1");
    }

    @Test
    void simulacaoDeDebitoCombinaDebitCardComDebvisaSemParcelas() {
        assertThat(service.payloadCenario("aprovado_debito"))
                .contains("\"payment_method_type\": \"debit_card\"")
                .contains("\"payment_method_id\": \"debvisa\"")
                .doesNotContain("installments");
    }

    @Test
    void simulacaoPixUsaQrSemPaymentMethodId() {
        assertThat(service.payloadCenario("aprovado_pix"))
                .contains("\"status\": \"processed\"")
                .contains("\"payment_method_type\": \"qr\"")
                .doesNotContain("payment_method_id")
                .doesNotContain("installments");
    }
}
