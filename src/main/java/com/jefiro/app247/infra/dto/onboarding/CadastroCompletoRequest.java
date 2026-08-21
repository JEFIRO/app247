package com.jefiro.app247.infra.dto.onboarding;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.jefiro.app247.domain.model.dto.CondominioRequest;
import com.jefiro.app247.domain.model.dto.EmpresaRequest;
import com.jefiro.app247.domain.model.dto.TerminalRequest;
import com.jefiro.app247.domain.model.dto.UserRequestDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CadastroCompletoRequest(
        @NotNull @Valid @JsonAlias("user") UserRequestDTO gestor,
        @NotNull @Valid EmpresaRequest empresa,
        @NotNull @Valid CondominioRequest condominio,
        @NotNull @Valid TerminalRequest terminal
) {
}
