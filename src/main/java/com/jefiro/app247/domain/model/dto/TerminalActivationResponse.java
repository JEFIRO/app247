package com.jefiro.app247.domain.model.dto;

import com.jefiro.app247.domain.model.terminal.Terminal;

public record TerminalActivationResponse(
        Long terminalId,
        String uuidTerminal,
        String serialNumber,
        String nome,
        String codigo,
        String status,
        Boolean ativo,
        Boolean activated,
        Long condominioId,
        String condominioNome
) {
    public TerminalActivationResponse(Terminal terminal) {
        this(terminal.getTerminal_id(), terminal.getUuid_terminal(), terminal.getSerialNumber(), terminal.getNome()
                , terminal.getCodigo(), terminal.getStatus().toString(), terminal.getAtivo(), terminal.getAtivo(), terminal.getCondominio().getCondominioId()
                , terminal.getCondominio().getNome());
    }

}