package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.dto.TerminalActivationResponse;
import com.jefiro.app247.domain.model.dto.TerminalStatusDTO;
import com.jefiro.app247.domain.model.enum_type.TerminalStatus;
import com.jefiro.app247.domain.model.terminal.Terminal;
import com.jefiro.app247.infra.exception.TerminalNotFoundException;
import com.jefiro.app247.infra.repository.TerminalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class TerminalService {

    @Autowired
    TerminalRepository repository;

    public Terminal save(Terminal terminal) {
        return repository.save(terminal);
    }

    public TerminalActivationResponse getBySerial(String serial) {

        return repository.findBySerialNumber(serial)
                .map(TerminalActivationResponse::new)
                .orElseThrow(() -> new RuntimeException(""));
    }

    public Terminal getTerminal(Long id) {
        return repository.findById(id).orElseThrow(TerminalNotFoundException::new);
    }

    public void updateStatus(TerminalStatusDTO status) {
        Terminal terminal = getTerminal(status.terminalId());

        terminal.setStatus(TerminalStatus.valueOf(status.status()));
        terminal.setUpdate_at(LocalDateTime.now());
        terminal.setLastPing(LocalDateTime.now());
        repository.save(terminal);
    }

    @Scheduled(fixedRate = 60000)
    public void verificarTerminais() {
        repository.findAll()
                .forEach(t -> {
                    if (t.getLastPing().isBefore(LocalDateTime.now().minusSeconds(60)) && t.getLastPing() != null) {
                        t.setStatus(TerminalStatus.OFFLINE);
                        repository.save(t);
                    }
                });
    }
}
