package com.jefiro.app247.domain.model.dto;

import com.jefiro.app247.domain.model.Empresa;

public record EmpresaBrandingResponse(
        String nomeExibicao,
        String logoUrl,
        String logoDarkUrl,
        String corPrincipal,
        String corSecundaria,
        String corDestaque
) {
    public static EmpresaBrandingResponse from(Empresa empresa) {
        String nome = empresa.getNomeExibicao();
        if (nome == null || nome.isBlank()) {
            nome = empresa.getNomeFantasia() == null || empresa.getNomeFantasia().isBlank()
                    ? empresa.getRazaoSocial() : empresa.getNomeFantasia();
        }
        return new EmpresaBrandingResponse(
                nome,
                empresa.getLogoUrl(),
                empresa.getLogoDarkUrl(),
                fallback(empresa.getCorPrincipal(), "#169DFF"),
                fallback(empresa.getCorSecundaria(), "#62C8FF"),
                fallback(empresa.getCorDestaque(), "#00D084")
        );
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
