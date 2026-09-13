package com.jefiro.app247.infra.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class TimePolicyTest {
    @Test
    void horarioAdministrativoDeSaoPauloViraInstantUtcEVoltaSemPerda() {
        LocalDateTime local = LocalDateTime.of(2026, 8, 30, 8, 0);

        Instant utc = TimePolicy.fromDisplayTime(local);

        assertThat(utc).isEqualTo(Instant.parse("2026-08-30T11:00:00Z"));
        assertThat(TimePolicy.toDisplayTime(utc)).isEqualTo(local);
    }

    @Test
    void hojeUsaViradaDoDiaDeSaoPauloMesmoQuandoAindaEhDiaAnteriorEmUtc() {
        TimePolicy.DateRange range = TimePolicy.today(Instant.parse("2026-09-05T02:30:00Z"));

        assertThat(range.from()).isEqualTo(Instant.parse("2026-09-04T03:00:00Z"));
        assertThat(range.to()).isEqualTo(Instant.parse("2026-09-05T03:00:00Z"));
    }

    @Test
    void rejeitaIntervaloMaiorQueUmAno() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                TimePolicy.range(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 2)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
