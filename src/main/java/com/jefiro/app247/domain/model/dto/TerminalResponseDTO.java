package com.jefiro.app247.domain.model.dto;

import com.jefiro.app247.domain.model.terminal.Terminal;
import com.jefiro.app247.domain.model.enum_type.TerminalStatus;

import java.time.Instant;

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
        String versaoSoftware,
        String mercadoPagoTerminalId,
        Instant lastPing,
        String lifecycleState
) {
    public TerminalResponseDTO(Terminal terminal) {
        this(terminal.getIdTerminal(), terminal.getNome(), terminal.getCodigo(), terminal.getSerialNumber(),
                terminal.getMacAddress(), terminal.getIpAddress(), terminal.getAtivo(), terminal.getStatus(),
                terminal.getCondominio().getIdCondominio(), terminal.getVersaoSoftware(),
                terminal.getMercadoPagoTerminalId(), terminal.getLastPing(),
                terminal.getLifecycleState().name());
    }
}
