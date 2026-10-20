package com.jefiro.app247.infra.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

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

    @Test
    void cancelarOrderCodificaOrderIdComoUmUnicoSegmento() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        TestarWebhookMP isolado = new TestarWebhookMP(restTemplate, null, "token-de-teste", "TERM");
        server.expect(requestTo(URI.create("https://api.mercadopago.com/v1/orders/..%2Fpayments%3Fx=1/cancel")))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());

        isolado.cancelarOrder("../payments?x=1");

        server.verify();
    }

    @Test
    void listarOrdersNaoPermiteInjetarParametrosPeloStatus() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        TestarWebhookMP isolado = new TestarWebhookMP(restTemplate, null, "token-de-teste", "TERM");
        server.expect(request -> assertThat(request.getURI().getRawQuery())
                        .contains("status=created%26page_size%3D1000")
                        .contains("page_size=50"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        isolado.listarOrders("2026-08-01T00:00:00.000Z", "2026-08-02T00:00:00.000Z", "created&page_size=1000");

        server.verify();
    }
}
