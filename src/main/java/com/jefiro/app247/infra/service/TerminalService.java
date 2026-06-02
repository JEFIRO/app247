package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.dto.TerminalActivationResponse;
import com.jefiro.app247.domain.model.terminal.Terminal;
import com.jefiro.app247.infra.repository.TerminalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
}
