package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Condominio;
import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.enum_type.TerminalBootstrapState;
import com.jefiro.app247.domain.model.enum_type.TerminalLifecycleState;
import com.jefiro.app247.domain.model.enum_type.TerminalStatus;
import com.jefiro.app247.domain.model.terminal.Terminal;
import com.jefiro.app247.infra.event.TerminalFactoryResetRequiredEvent;
import com.jefiro.app247.infra.repository.TerminalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TerminalLifecycleServiceTest {
    @Mock TerminalRepository terminalRepository;
    @Mock MercadoPagoOperationalConfigurationService configurationService;
    @Mock TerminalPointBindingService pointBindingService;
    @Mock AuditLogService auditLogService;
    @Mock ApplicationEventPublisher eventPublisher;

    TerminalLifecycleService service;

    @BeforeEach
    void setUp() {
        service = new TerminalLifecycleService();
        ReflectionTestUtils.setField(service, "terminalRepository", terminalRepository);
        ReflectionTestUtils.setField(service, "configurationService", configurationService);
        ReflectionTestUtils.setField(service, "pointBindingService", pointBindingService);
        ReflectionTestUtils.setField(service, "auditLogService", auditLogService);
        ReflectionTestUtils.setField(service, "eventPublisher", eventPublisher);
    }

    @Test
    void encerramentoMarcaTodosOsTerminaisComResetDuravelEPublicaWebSocket() {
        Empresa empresa = empresa(true, false);
        Terminal online = terminal("terminal-online", empresa);
        Terminal offline = terminal("terminal-offline", empresa);
        offline.setStatus(TerminalStatus.OFFLINE);
        when(terminalRepository.findAllByCondominioEmpresaIdOrderByNome("empresa-a"))
                .thenReturn(List.of(online, offline));

        int alterados = service.marcarResetRequired(empresa, "COMPANY_CLOSED");

        assertThat(alterados).isEqualTo(2);
        assertThat(online.getLifecycleState()).isEqualTo(TerminalLifecycleState.RESET_REQUIRED);
        assertThat(offline.getLifecycleState()).isEqualTo(TerminalLifecycleState.RESET_REQUIRED);
        assertThat(online.getAtivo()).isFalse();
        verify(pointBindingService).desvincularDaEmpresa("empresa-a", "COMPANY_CLOSED");
        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(2)).publishEvent(events.capture());
        assertThat(events.getAllValues()).allMatch(TerminalFactoryResetRequiredEvent.class::isInstance);
    }

    @Test
    void terminalOfflineDescobreResetRequiredPeloBootstrapHttp() {
        Empresa empresa = empresa(false, true);
        Terminal terminal = terminal("terminal-offline", empresa);
        terminal.setLifecycleState(TerminalLifecycleState.RESET_REQUIRED);
        terminal.setResetReason("COMPANY_CLOSED");
        when(terminalRepository.findById("terminal-offline")).thenReturn(Optional.of(terminal));

        var response = service.consultar("terminal-offline");

        assertThat(response.state()).isEqualTo(TerminalBootstrapState.RESET_REQUIRED);
        assertThat(response.reason()).isEqualTo("COMPANY_CLOSED");
        assertThat(response.paymentConfigured()).isFalse();
        verifyNoInteractions(configurationService);
    }

    @Test
    void idAntigoInexistenteRecebeConfirmacaoAutoritativaDeReset() {
        when(terminalRepository.findById("terminal-antigo")).thenReturn(Optional.empty());

        var response = service.consultar("terminal-antigo");

        assertThat(response.state()).isEqualTo(TerminalBootstrapState.RESET_REQUIRED);
        assertThat(response.reason()).isEqualTo("TERMINAL_NOT_FOUND");
    }

    @Test
    void suspensaoTemporariaBloqueiaSemSolicitarFactoryReset() {
        Empresa empresa = empresa(false, false);
        Terminal terminal = terminal("terminal-disabled", empresa);
        when(terminalRepository.findById("terminal-disabled")).thenReturn(Optional.of(terminal));

        var response = service.consultar("terminal-disabled");

        assertThat(response.state()).isEqualTo(TerminalBootstrapState.DISABLED);
        assertThat(terminal.getLifecycleState()).isEqualTo(TerminalLifecycleState.ACTIVE);
        verifyNoInteractions(configurationService, eventPublisher);
    }

    @Test
    void confirmacaoEhIdempotenteELiberaIdentidadeFisicaParaNovaAtivacao() {
        Empresa empresa = empresa(false, true);
        Terminal terminal = terminal("terminal-reset", empresa);
        terminal.setLifecycleState(TerminalLifecycleState.RESET_REQUIRED);
        terminal.setSerialNumber("raspberry-serial");
        terminal.setMacAddress("aa:bb:cc:dd:ee:ff");
        when(terminalRepository.findById("terminal-reset"))
                .thenReturn(Optional.of(terminal), Optional.of(terminal));

        service.confirmarConclusao("terminal-reset");
        service.confirmarConclusao("terminal-reset");

        assertThat(terminal.getLifecycleState()).isEqualTo(TerminalLifecycleState.UNACTIVATED);
        assertThat(terminal.getSerialNumber()).isNull();
        assertThat(terminal.getMacAddress()).isNull();
        assertThat(terminal.getStatus()).isEqualTo(TerminalStatus.OFFLINE);
        verify(terminalRepository, times(1)).save(terminal);
    }

    private Empresa empresa(boolean ativa, boolean encerrada) {
        return Empresa.builder().id("empresa-a").ativo(ativa)
                .encerradaEm(encerrada ? java.time.Instant.now() : null).build();
    }

    private Terminal terminal(String id, Empresa empresa) {
        Condominio condominio = new Condominio();
        condominio.setIdCondominio("condominio-a");
        condominio.setEmpresa(empresa);
        Terminal terminal = new Terminal();
        terminal.setIdTerminal(id);
        terminal.setCondominio(condominio);
        terminal.setNome(id);
        terminal.setCodigo(id);
        terminal.setAtivo(true);
        terminal.setStatus(TerminalStatus.ONLINE);
        terminal.setLifecycleState(TerminalLifecycleState.ACTIVE);
        terminal.setMercadoPagoTerminalId("point-" + id);
        return terminal;
    }
}
