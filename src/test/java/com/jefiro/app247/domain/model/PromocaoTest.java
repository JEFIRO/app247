package com.jefiro.app247.domain.model;

import com.jefiro.app247.domain.model.enum_type.StatusPromocao;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PromocaoTest {
    @Test
    void statusEhCalculadoPorHabilitacaoEPeriodo() {
        Promocao promocao = new Promocao();
        promocao.setInicio(Instant.parse("2026-08-30T11:00:00Z"));
        promocao.setFim(Instant.parse("2026-08-30T17:00:00Z"));

        promocao.setAtivo(false);
        assertEquals(StatusPromocao.DESATIVADA,
                promocao.statusEm(Instant.parse("2026-08-30T13:00:00Z")));
        promocao.setAtivo(true);
        assertEquals(StatusPromocao.AGENDADA,
                promocao.statusEm(Instant.parse("2026-08-30T10:59:00Z")));
        assertEquals(StatusPromocao.ATIVA,
                promocao.statusEm(Instant.parse("2026-08-30T11:00:00Z")));
        assertEquals(StatusPromocao.ENCERRADA,
                promocao.statusEm(Instant.parse("2026-08-30T17:00:00Z")));
    }
}
