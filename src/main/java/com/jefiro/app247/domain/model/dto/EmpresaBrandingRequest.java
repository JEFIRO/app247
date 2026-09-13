package com.jefiro.app247.domain.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EmpresaBrandingRequest(
        @NotBlank(message = "Nome de exibição é obrigatório")
        @Size(max = 150, message = "Nome de exibição deve ter no máximo 150 caracteres")
        String nomeExibicao,

        @Pattern(regexp = "^https://[^\\s]{1,492}$", message = "Logo deve usar uma URL HTTPS válida")
        String logoUrl,

        @Pattern(regexp = "^https://[^\\s]{1,492}$", message = "Logo escuro deve usar uma URL HTTPS válida")
        String logoDarkUrl,

        @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Cor principal deve usar #RRGGBB")
        String corPrincipal,

        @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Cor secundária deve usar #RRGGBB")
        String corSecundaria,

        @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Cor de destaque deve usar #RRGGBB")
        String corDestaque
) {
}
