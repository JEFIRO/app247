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
                "Defina o estoque mínimo no cadastro dos produtos para habilitar este indicador"
        );
    }

    public static AdminStockSummaryResponse abaixoDoMinimo(long produtos) {
        return new AdminStockSummaryResponse(true, produtos, null);
    }
}
