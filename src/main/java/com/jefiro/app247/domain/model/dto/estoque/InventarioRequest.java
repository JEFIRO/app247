package com.jefiro.app247.domain.model.dto.estoque;

import com.jefiro.app247.domain.model.enum_type.TipoLocalEstoque;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InventarioRequest(
        @NotNull TipoLocalEstoque localTipo,
        String localId,
        @Size(max = 180) String descricao,
        @Size(max = 600) String observacao,
        boolean contagemCega
) {}
