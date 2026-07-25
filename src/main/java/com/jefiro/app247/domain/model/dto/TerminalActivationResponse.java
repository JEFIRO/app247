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
        String condominioNome
) {
    public TerminalActivationResponse(Terminal terminal) {
        this(terminal.getIdTerminal(), terminal.getSerialNumber(), terminal.getNome()
                , terminal.getCodigo(), terminal.getStatus().toString(), terminal.getAtivo(), terminal.getAtivo(), terminal.getCondominio().getIdCondominio()
                , terminal.getCondominio().getNome());
    }

}