package com.jefiro.app247.domain.model.dto.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jefiro.app247.domain.model.enum_type.TelemetryAlertStatus;
import com.jefiro.app247.domain.model.enum_type.TelemetryAlertType;
import com.jefiro.app247.domain.model.enum_type.TerminalOperationalStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdminAlertResponseTest {
    @Test
    void severidadeCalculadaFazParteDoContratoJson() throws Exception {
        var response = new AdminAlertResponse(
                "alerta", "terminal", "Entrada", "condominio", "Central",
                TelemetryAlertType.TERMINAL_OFFLINE, TelemetryAlertStatus.ACTIVE,
                TerminalOperationalStatus.OFFLINE, "Terminal offline",
                null, null, null);

        String json = new ObjectMapper().writeValueAsString(response);

        assertThat(json).contains("\"severidade\":\"CRITICAL\"");
    }
}
