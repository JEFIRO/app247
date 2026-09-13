package com.jefiro.app247.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentAttemptTest {

    private static final String ORDER_ID = "2bdd3dd4-6622-4e62-b0cc-1685cb72f26c";

    @Test
    void geraExternalReferenceAceitaPeloMercadoPago() {
        PaymentAttempt attempt = attempt();

        attempt.prePersist();

        assertThat(attempt.getExternalReference())
                .isEqualTo(ORDER_ID + "-1")
                .matches("[A-Za-z0-9_-]{1,64}");
    }

    @Test
    void normalizaReferenciaLegadaAntesDeReenviarTentativaSemOrderRemota() {
        PaymentAttempt attempt = attempt();
        attempt.setExternalReference(ORDER_ID + ":1");
        attempt.setIdempotencyKey("attempt-chave-antiga");

        attempt.preUpdate();

        assertThat(attempt.getExternalReference()).isEqualTo(ORDER_ID + "-1");
        assertThat(attempt.getIdempotencyKey())
                .startsWith("attempt-")
                .isNotEqualTo("attempt-chave-antiga");
    }

    @Test
    void preservaReferenciaLegadaDeTentativaJaAceitaRemotamente() {
        PaymentAttempt attempt = attempt();
        attempt.setExternalReference(ORDER_ID + ":1");
        attempt.setIdempotencyKey("attempt-chave-antiga");
        attempt.setProviderOrderId("ORD123");

        attempt.preUpdate();

        assertThat(attempt.getExternalReference()).isEqualTo(ORDER_ID + ":1");
        assertThat(attempt.getIdempotencyKey()).isEqualTo("attempt-chave-antiga");
    }

    private PaymentAttempt attempt() {
        Order order = new Order();
        order.setIdOrder(ORDER_ID);
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setOrder(order);
        attempt.setAttemptNumber(1);
        return attempt;
    }
}
