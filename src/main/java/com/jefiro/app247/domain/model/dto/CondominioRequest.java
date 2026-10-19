package com.jefiro.app247.domain.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CondominioRequest(
        @NotBlank(message = "Nome do condomínio é obrigatório")
        @Size(max = 150, message = "Nome do condomínio deve ter no máximo 150 caracteres")
        String nome,
        // O admin envia "" quando o campo fica vazio; aceita só dígitos ou o formato pontuado.
        @Pattern(regexp = "|\\d{14}|\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}", message = "CNPJ inválido")
        String cnpj,
        @Valid
        EnderecoDTO endereco
) {
}
