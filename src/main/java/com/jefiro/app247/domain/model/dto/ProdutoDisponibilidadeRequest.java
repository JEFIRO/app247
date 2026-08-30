package com.jefiro.app247.domain.model.dto;

import jakarta.validation.constraints.NotNull;

public record ProdutoDisponibilidadeRequest(@NotNull Boolean ativo) {}
