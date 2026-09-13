package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.TerminalTelemetryCurrent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TerminalTelemetryCurrentRepository extends JpaRepository<TerminalTelemetryCurrent, String> {
    Optional<TerminalTelemetryCurrent> findByTerminal_IdTerminalAndTerminal_Condominio_Empresa_Id(
            String terminalId, String empresaId);
    List<TerminalTelemetryCurrent> findAllByTerminal_Condominio_Empresa_Id(String empresaId);
}
