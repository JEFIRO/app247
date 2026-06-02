package com.jefiro.app247.domain.model.dto;

public record CadastroCompletoRequest(
        UserRequestDTO user,
        CondominioRequest condominio,
        TerminalRequest terminal
) {}