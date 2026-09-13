package com.jefiro.app247.domain.model.dto;

import com.jefiro.app247.domain.model.terminal.Terminal;

public record TerminalActivationResponse(
        String terminalId,
        String serialNumber,
        String nome,
        String codigo,
        String status,
        Boolean ativo,
        Boolean activated,
        String condominioId,
        String condominioNome,
        String lifecycleState
) {
    public TerminalActivationResponse(Terminal terminal) {
        this(terminal.getIdTerminal(), terminal.getSerialNumber(), terminal.getNome()
                , terminal.getCodigo(), terminal.getStatus().toString(), terminal.getAtivo()
                , terminal.getLifecycleState() == com.jefiro.app247.domain.model.enum_type.TerminalLifecycleState.ACTIVE
                        && Boolean.TRUE.equals(terminal.getAtivo())
                        && Boolean.TRUE.equals(terminal.getCondominio().getEmpresa().getAtivo())
                        && !terminal.getCondominio().getEmpresa().isDefinitivamenteEncerrada()
                , terminal.getCondominio().getIdCondominio(), terminal.getCondominio().getNome()
                , terminal.getLifecycleState().name());
    }

}
