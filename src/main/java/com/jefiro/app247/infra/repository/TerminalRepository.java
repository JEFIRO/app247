package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.terminal.Terminal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TerminalRepository extends JpaRepository<Terminal, Long> {
    Optional<Terminal> findBySerialNumber(String serialNumber);

}
