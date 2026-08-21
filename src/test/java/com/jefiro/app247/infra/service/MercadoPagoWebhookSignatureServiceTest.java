package com.jefiro.app247.infra.service;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class MercadoPagoWebhookSignatureServiceTest {

    @Test
    void validaAssinaturaOficialComDataIdEmMinusculas() throws Exception {
        String secret = "segredo-de-teste";
        String manifest = "id:ord01abc;request-id:req-1;ts:1742505638683;";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String hash = HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));

        MercadoPagoWebhookSignatureService service = new MercadoPagoWebhookSignatureService(secret);

        assertThat(service.isValid(
                "ts=1742505638683,v1=" + hash,
                "req-1",
                "ORD01ABC"
        )).isTrue();
        assertThat(service.isValid("ts=1742505638683,v1=invalid", "req-1", "ORD01ABC")).isFalse();
    }
}
