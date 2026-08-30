package com.jefiro.app247.domain.model.dto;

import com.jefiro.app247.domain.model.enum_type.AbrangenciaPromocao;
import com.jefiro.app247.domain.model.enum_type.TipoPromocao;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

public record PromocaoRequest(
        @NotBlank @Size(max = 150) String nome,
        @Size(max = 500) String descricao,
        @NotNull AbrangenciaPromocao abrangencia,
        String condominioId,
        @NotNull TipoPromocao tipo,
        @NotNull BigDecimal valor,
        @NotNull Instant inicio,
        @NotNull Instant fim,
        Boolean ativo,
        Integer prioridade,
        @NotEmpty Set<@NotBlank String> produtoIds
) {
}
