package com.jefiro.app247.domain.model.dto.admin;

public record AdminStockSummaryResponse(
        boolean regraConfigurada,
        Long produtosBaixoEstoque,
        String motivoIndisponibilidade
) {
    public static AdminStockSummaryResponse regraIndisponivel() {
        return new AdminStockSummaryResponse(
                false,
                null,
                "Defina um estoque mínimo por local para habilitar este indicador"
        );
    }
}
