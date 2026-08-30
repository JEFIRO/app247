package com.jefiro.app247.infra.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyPolicy {
    public static final int PERSISTENCE_SCALE = 6;
    public static final int CALCULATION_SCALE = 12;
    public static final int CHARGED_SCALE = 2;
    public static final RoundingMode INTERNAL_ROUNDING = RoundingMode.HALF_UP;
    public static final RoundingMode CHARGED_ROUNDING = RoundingMode.HALF_UP;

    private MoneyPolicy() {
    }

    public static BigDecimal persistence(BigDecimal value) {
        require(value);
        return value.setScale(PERSISTENCE_SCALE, INTERNAL_ROUNDING);
    }

    public static BigDecimal charged(BigDecimal preciseTotal) {
        require(preciseTotal);
        return preciseTotal.setScale(CHARGED_SCALE, CHARGED_ROUNDING);
    }

    public static BigDecimal chargedForPersistence(BigDecimal preciseTotal) {
        return charged(preciseTotal).setScale(PERSISTENCE_SCALE, RoundingMode.UNNECESSARY);
    }

    public static BigDecimal percentageFactor(BigDecimal percentage) {
        require(percentage);
        return BigDecimal.ONE.subtract(percentage.movePointLeft(2));
    }

    private static void require(BigDecimal value) {
        if (value == null) throw new IllegalArgumentException("Valor monetário não pode ser nulo");
    }
}
