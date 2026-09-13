package com.jefiro.app247.infra.service.admin;

import com.jefiro.app247.infra.service.TimePolicy;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;

@Service
public class AdminPeriodService {

    public TimePolicy.DateRange resolve(String period, LocalDate from, LocalDate to, Instant now) {
        if (from != null || to != null) {
            return TimePolicy.range(from, to);
        }

        LocalDate today = now.atZone(TimePolicy.DISPLAY_ZONE).toLocalDate();
        String normalized = period == null || period.isBlank()
                ? "TODAY"
                : period.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "TODAY" -> TimePolicy.range(today, today);
            case "7D" -> TimePolicy.range(today.minusDays(6), today);
            case "30D" -> TimePolicy.range(today.minusDays(29), today);
            default -> throw new IllegalArgumentException("Período deve ser TODAY, 7D ou 30D");
        };
    }
}
