package com.jefiro.app247.domain.model.dto;

import com.jefiro.app247.domain.model.terminal.Terminal;
import com.jefiro.app247.domain.model.enum_type.TerminalStatus;

import java.time.LocalDateTime;

public record TerminalResponseDTO(
        String id,
        String nome,
        String codigo,
        String serialNumber,
        String macAddress,
        String ipAddress,
        Boolean ativo,
        TerminalStatus status,
        String condominioId,
        String mercadoPagoTerminalId,
        LocalDateTime lastPing
) {
    public TerminalResponseDTO(Terminal terminal) {
        this(terminal.getIdTerminal(), terminal.getNome(), terminal.getCodigo(), terminal.getSerialNumber(),
                terminal.getMacAddress(), terminal.getIpAddress(), terminal.getAtivo(), terminal.getStatus(),
                terminal.getCondominio().getIdCondominio(), terminal.getMercadoPagoTerminalId(), terminal.getLastPing());
    }
}
