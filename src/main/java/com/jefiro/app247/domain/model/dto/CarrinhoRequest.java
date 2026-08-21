package com.jefiro.app247.domain.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CarrinhoRequest(
        @NotBlank String terminalId,
        @NotEmpty List<@NotNull @Valid ItemRequest> items

) {
}
