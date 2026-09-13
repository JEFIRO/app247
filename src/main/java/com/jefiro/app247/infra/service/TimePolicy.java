package com.jefiro.app247.infra.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Política temporal única: persistência absoluta em UTC e apresentação em São Paulo. */
public final class TimePolicy {
    public static final ZoneId DISPLAY_ZONE = ZoneId.of("America/Sao_Paulo");

    private TimePolicy() {}

    public static Instant fromDisplayTime(LocalDateTime localDateTime) {
        if (localDateTime == null) return null;
        return localDateTime.atZone(DISPLAY_ZONE).toInstant();
    }

    public static LocalDateTime toDisplayTime(Instant instant) {
        if (instant == null) return null;
        return LocalDateTime.ofInstant(instant, DISPLAY_ZONE);
    }

    public static DateRange today(Instant now) {
        LocalDate date = now.atZone(DISPLAY_ZONE).toLocalDate();
        return range(date, date);
    }

    public static DateRange range(LocalDate from, LocalDate toInclusive) {
        if (from == null || toInclusive == null) {
            throw new IllegalArgumentException("Informe as duas datas do período");
        }
        if (toInclusive.isBefore(from)) {
            throw new IllegalArgumentException("A data final deve ser igual ou posterior à inicial");
        }
        if (from.plusYears(1).isBefore(toInclusive)) {
            throw new IllegalArgumentException("O período máximo permitido é de um ano");
        }
        return new DateRange(
                from.atStartOfDay(DISPLAY_ZONE).toInstant(),
                toInclusive.plusDays(1).atStartOfDay(DISPLAY_ZONE).toInstant()
        );
    }

    public record DateRange(Instant from, Instant to) {
    }
}
