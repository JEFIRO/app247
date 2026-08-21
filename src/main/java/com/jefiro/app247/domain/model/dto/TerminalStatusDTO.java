package com.jefiro.app247.domain.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TerminalStatusDTO(

        @NotNull
String terminalId,

        @NotBlank
        String status

) {
}
