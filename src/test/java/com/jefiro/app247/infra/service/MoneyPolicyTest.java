package com.jefiro.app247.infra.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MoneyPolicyTest {
    @Test
    void percentualMantemSeisCasasInternas() {
        BigDecimal resultado = MoneyPolicy.persistence(
                new BigDecimal("10.990000").multiply(
                        MoneyPolicy.percentageFactor(new BigDecimal("7.500000"))));

        assertEquals(new BigDecimal("10.165750"), resultado);
    }

    @Test
    void somaAntesDeArredondarTotalCobrado() {
        BigDecimal preciso = new BigDecimal("10.165750")
                .add(new BigDecimal("7.347625"))
                .add(new BigDecimal("5.127500"));

        assertEquals(new BigDecimal("22.640875"), preciso);
        assertEquals(new BigDecimal("22.64"), MoneyPolicy.charged(preciso));
        assertEquals(new BigDecimal("22.640000"), MoneyPolicy.chargedForPersistence(preciso));
    }

    @Test
    void arredondaSomenteTotalFinalComHalfUp() {
        assertEquals(new BigDecimal("10.17"), MoneyPolicy.charged(new BigDecimal("10.165750")));
    }
}
