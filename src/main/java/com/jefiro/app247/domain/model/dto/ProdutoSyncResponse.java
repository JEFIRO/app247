package com.jefiro.app247.domain.model.dto;

import java.time.Instant;
import java.util.List;

public record ProdutoSyncResponse(
        Instant syncAt,
        boolean fullSync,
        List<ProdutoSyncChange> changes
) {}
