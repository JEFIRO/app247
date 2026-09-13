package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Condominio;
import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.TerminalTelemetryCurrent;
import com.jefiro.app247.domain.model.dto.TerminalTelemetryRequest;
import com.jefiro.app247.domain.model.enum_type.TerminalOperationalStatus;
import com.jefiro.app247.domain.model.terminal.Terminal;
import com.jefiro.app247.infra.exception.TerminalNotFoundException;
import com.jefiro.app247.infra.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TerminalTelemetryServiceTest {
    @Mock TerminalRepository terminalRepository;
    @Mock TerminalTelemetryCurrentRepository currentRepository;
    @Mock TerminalTelemetryHistoryRepository historyRepository;
    @Mock TerminalTelemetryAlertRepository alertRepository;
    @Mock TelemetryHealthService healthService;
    @Mock TelemetryAlertService alertService;
    @Mock TerminalPresenceService presenceService;

    @AfterEach void clearTenant() { EmpresaContext.clear(); }

    @Test
    void recebeValidaPersisteHistoricoEFazUpsertDoEstadoAtual() {
        var terminal = terminal("terminal-a", "empresa-a");
        when(terminalRepository.findByIdForTelemetryUpdate("terminal-a")).thenReturn(Optional.of(terminal));
        when(currentRepository.findById("terminal-a")).thenReturn(Optional.empty());
        when(healthService.assess(any(), eq(false), any())).thenReturn(
                new TelemetryHealthService.Assessment(
                        TerminalOperationalStatus.SAUDAVEL, List.of(), Map.of()));
        when(presenceService.isOnline(terminal)).thenReturn(true);
        when(alertRepository.findByTerminalIdTerminalAndStatusOrderByOpenedAtDesc(any(), any()))
                .thenReturn(List.of());

        var response = service().receive(request("terminal-a"));

        assertThat(response.terminalId()).isEqualTo("terminal-a");
        assertThat(response.online()).isTrue();
        verify(historyRepository).save(any());
        verify(currentRepository).save(any(TerminalTelemetryCurrent.class));
        verify(alertService).reconcile(eq(terminal), eq(Map.of()), any());
        verify(terminalRepository).save(terminal);
        assertThat(terminal.getVersaoSoftware()).isEqualTo("1.2.3");
        assertThat(terminal.getIpAddress()).isEqualTo("192.168.1.4");
    }

    @Test
    void rejeitaTerminalInexistente() {
        when(terminalRepository.findByIdForTelemetryUpdate("desconhecido")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().receive(request("desconhecido")))
                .isInstanceOf(TerminalNotFoundException.class);
        verifyNoInteractions(historyRepository, currentRepository);
    }

    @Test
    void detalheNaoPermiteAcessoEntreEmpresas() {
        EmpresaContext.set("empresa-a");
        when(terminalRepository.findByIdTerminalAndCondominioEmpresaId("terminal-b", "empresa-a"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().detail("terminal-b"))
                .isInstanceOf(TerminalNotFoundException.class);
        verifyNoInteractions(currentRepository);
    }

    private TerminalTelemetryService service() {
        return new TerminalTelemetryService(terminalRepository, currentRepository, historyRepository,
                alertRepository, healthService, alertService, presenceService);
    }

    private TerminalTelemetryRequest request(String terminalId) {
        return new TerminalTelemetryRequest(terminalId, Instant.now(),
                new TerminalTelemetryRequest.SystemMetrics(
                        100L, 20d, 55d, 500L, 1000L, 50d,
                        100L, 1000L, 10d, 0.5d,
                        false, false, false, false, false, false, false, false, "0x0"),
                new TerminalTelemetryRequest.NetworkMetrics(
                        true, "wlan0", "Rede", "192.168.1.4", 80, "EXCELENTE", true, 40L),
                new TerminalTelemetryRequest.ApplicationMetrics(
                        "1.2.3", 80L, "CONNECTED", null, null, Instant.now(), null, false, false),
                new TerminalTelemetryRequest.DisplayMetrics(1024, 600, "HORIZONTAL"));
    }

    private Terminal terminal(String id, String empresaId) {
        var empresa = Empresa.builder().id(empresaId).build();
        var condominio = new Condominio();
        condominio.setIdCondominio("cond-a");
        condominio.setNome("Firenze");
        condominio.setEmpresa(empresa);
        var terminal = new Terminal();
        terminal.setIdTerminal(id);
        terminal.setNome("Terminal 01");
        terminal.setAtivo(true);
        terminal.setCondominio(condominio);
        return terminal;
    }
}
