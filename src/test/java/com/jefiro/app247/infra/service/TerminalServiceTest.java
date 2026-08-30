package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.dto.TerminalStatusDTO;
import com.jefiro.app247.domain.model.enum_type.TerminalStatus;
import com.jefiro.app247.domain.model.terminal.Terminal;
import com.jefiro.app247.infra.repository.TerminalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TerminalServiceTest {
    @Mock TerminalRepository repository;
    @Mock CondominioService condominioService;
    @InjectMocks TerminalService service;

    @Test
    void heartbeatAtualizaTerminalCorretoEConfirmaLastPingPersistido() {
        Terminal terminal = new Terminal();
        terminal.setIdTerminal("terminal-a");
        terminal.setStatus(TerminalStatus.OFFLINE);
        LocalDateTime anterior = LocalDateTime.now().minusMinutes(1);
        terminal.setLastPing(anterior);
        when(repository.findById("terminal-a")).thenReturn(Optional.of(terminal));
        when(repository.saveAndFlush(any(Terminal.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Terminal salvo = service.updateStatus(new TerminalStatusDTO("terminal-a", "ONLINE"));

        assertThat(salvo.getIdTerminal()).isEqualTo("terminal-a");
        assertThat(salvo.getStatus()).isEqualTo(TerminalStatus.ONLINE);
        assertThat(salvo.getLastPing()).isAfter(anterior);
        assertThat(salvo.getUpdate_at()).isNotNull();
    }
}
