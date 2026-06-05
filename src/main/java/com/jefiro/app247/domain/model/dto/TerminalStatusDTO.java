package com.jefiro.app247.domain.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TerminalStatusDTO(

        @NotNull
@Positive
Long terminalId,

        @NotBlank
        String status

) {
}