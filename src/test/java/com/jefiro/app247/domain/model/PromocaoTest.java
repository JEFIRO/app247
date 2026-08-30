package com.jefiro.app247.domain.model;

import com.jefiro.app247.domain.model.enum_type.StatusPromocao;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PromocaoTest {
    @Test
    void statusEhCalculadoPorHabilitacaoEPeriodo() {
        Promocao promocao = new Promocao();
        promocao.setInicio(LocalDateTime.of(2026, 8, 30, 8, 0));
        promocao.setFim(LocalDateTime.of(2026, 8, 30, 14, 0));

        promocao.setAtivo(false);
        assertEquals(StatusPromocao.DESATIVADA,
                promocao.statusEm(LocalDateTime.of(2026, 8, 30, 10, 0)));
        promocao.setAtivo(true);
        assertEquals(StatusPromocao.AGENDADA,
                promocao.statusEm(LocalDateTime.of(2026, 8, 30, 7, 59)));
        assertEquals(StatusPromocao.ATIVA,
                promocao.statusEm(LocalDateTime.of(2026, 8, 30, 8, 0)));
        assertEquals(StatusPromocao.ENCERRADA,
                promocao.statusEm(LocalDateTime.of(2026, 8, 30, 14, 0)));
    }
}
