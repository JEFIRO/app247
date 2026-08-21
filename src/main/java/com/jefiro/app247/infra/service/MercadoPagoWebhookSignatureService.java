package com.jefiro.app247.infra.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class MercadoPagoWebhookSignatureService {

    private final String secret;

    public MercadoPagoWebhookSignatureService(
            @Value("${api.mercado.pago.webhook.secret:}") String secret
    ) {
        this.secret = secret;
    }

    public boolean isConfigured() {
        return secret != null && !secret.isBlank();
    }

    public boolean isValid(String xSignature, String xRequestId, String dataId) {
        if (!isConfigured() || xSignature == null || xSignature.isBlank()) {
            return false;
        }

        String timestamp = null;
        String receivedHash = null;
        for (String part : xSignature.split(",")) {
            String[] keyValue = part.split("=", 2);
            if (keyValue.length != 2) {
                continue;
            }
            String key = keyValue[0].trim();
            String value = keyValue[1].trim();
            if ("ts".equals(key)) {
                timestamp = value;
            } else if ("v1".equals(key)) {
                receivedHash = value;
            }
        }

        if (timestamp == null || receivedHash == null) {
            return false;
        }

        StringBuilder manifest = new StringBuilder();
        if (dataId != null && !dataId.isBlank()) {
            manifest.append("id:").append(dataId.toLowerCase()).append(';');
        }
        if (xRequestId != null && !xRequestId.isBlank()) {
            manifest.append("request-id:").append(xRequestId).append(';');
        }
        manifest.append("ts:").append(timestamp).append(';');

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String computed = HexFormat.of().formatHex(
                    mac.doFinal(manifest.toString().getBytes(StandardCharsets.UTF_8))
            );
            return MessageDigest.isEqual(
                    computed.getBytes(StandardCharsets.US_ASCII),
                    receivedHash.getBytes(StandardCharsets.US_ASCII)
            );
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível validar a assinatura do webhook", e);
        }
    }
}
