package com.jefiro.app247.domain.model.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClientRegistrationRequest(
        @NotBlank(message = "Nome completo é obrigatório") @Size(max = 200)
        String nome,
        @Size(max = 100)
        String sobrenome,
        @NotBlank(message = "CPF é obrigatório")
        String cpf,
        @NotBlank(message = "E-mail é obrigatório") @Email(message = "E-mail inválido") @Size(max = 180)
        String email,
        @Size(max = 20)
        String telefone,
        @NotBlank(message = "Senha é obrigatória") @Size(min = 6, max = 72, message = "A senha deve ter entre 6 e 72 caracteres")
        String senha,
        Boolean termsAccepted,
        Boolean privacyAccepted,
        String termsVersion,
        String privacyVersion
) {
    public ClientRegistrationRequest {
        if (email != null) email = email.trim();
    }
}
