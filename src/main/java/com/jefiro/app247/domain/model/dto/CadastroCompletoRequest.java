package com.jefiro.app247.domain.model.dto;

public record CadastroCompletoRequest(
        UserRequestDTO user,
        EmpresaRequest empresa,
        CondominioRequest condominio,
        TerminalRequest terminal
) {}