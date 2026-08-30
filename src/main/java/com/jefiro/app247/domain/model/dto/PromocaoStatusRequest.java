package com.jefiro.app247.domain.model.dto;

import jakarta.validation.constraints.NotNull;

public record PromocaoStatusRequest(@NotNull Boolean ativo) {
}
