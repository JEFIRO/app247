package com.jefiro.app247.domain.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Limites espelham as colunas de {@code endereco}. */
public record EnderecoDTO(
        @NotBlank(message = "Rua é obrigatória") @Size(max = 180, message = "Rua deve ter no máximo 180 caracteres")
        String rua,
        @NotBlank(message = "Número é obrigatório") @Size(max = 20, message = "Número deve ter no máximo 20 caracteres")
        String numero,
        @Size(max = 100, message = "Complemento deve ter no máximo 100 caracteres")
        String complemento,
        @NotBlank(message = "Bairro é obrigatório") @Size(max = 100, message = "Bairro deve ter no máximo 100 caracteres")
        String bairro,
        @NotBlank(message = "Cidade é obrigatória") @Size(max = 100, message = "Cidade deve ter no máximo 100 caracteres")
        String cidade,
        @NotBlank(message = "Estado é obrigatório") @Pattern(regexp = "[A-Za-z]{2}", message = "Estado deve ser a sigla UF")
        String estado,
        @NotBlank(message = "CEP é obrigatório") @Pattern(regexp = "\\d{5}-?\\d{3}", message = "CEP inválido")
        String cep
) {
}
