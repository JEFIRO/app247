package com.jefiro.app247.domain.model.dto.admin;

public record AdminOnboardingStatusResponse(
        boolean empresaCadastrada,
        boolean condominioCadastrado,
        boolean terminalAtivado,
        boolean mercadoPagoVinculado,
        boolean pointVinculada,
        boolean produtosCadastrados,
        boolean produtosAssociadosCondominio
) {
    public boolean completo() {
        return empresaCadastrada
                && condominioCadastrado
                && terminalAtivado
                && mercadoPagoVinculado
                && pointVinculada
                && produtosCadastrados
                && produtosAssociadosCondominio;
    }
}
