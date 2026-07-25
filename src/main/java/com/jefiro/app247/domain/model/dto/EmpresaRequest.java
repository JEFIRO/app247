package com.jefiro.app247.domain.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EmpresaRequest(

        @NotBlank(message = "Razão social é obrigatória")
        @Size(max = 255, message = "Razão social deve ter no máximo 255 caracteres")
        String razaoSocial,

        @NotBlank(message = "Nome fantasia é obrigatório")
        @Size(max = 255, message = "Nome fantasia deve ter no máximo 255 caracteres")
        String nomeFantasia,

        @NotBlank(message = "CNPJ é obrigatório")
        @Pattern(
                regexp = "\\d{14}",
                message = "CNPJ deve conter exatamente 14 números"
        )
        String cnpj,

        @NotBlank(message = "E-mail é obrigatório")
        @Email(message = "E-mail inválido")
        String email,

        @NotBlank(message = "Telefone é obrigatório")
        @Pattern(
                regexp = "\\d{10,11}",
                message = "Telefone deve conter 10 ou 11 números"
        )
        String telefone,

        @NotBlank(message = "CEP é obrigatório")
        @Pattern(
                regexp = "\\d{8}",
                message = "CEP deve conter exatamente 8 números"
        )
        String cep,

        @NotBlank(message = "Logradouro é obrigatório")
        String logradouro,

        @NotBlank(message = "Número é obrigatório")
        String numero,

        @NotBlank(message = "Bairro é obrigatório")
        String bairro,

        @NotBlank(message = "Cidade é obrigatória")
        String cidade,

        @NotBlank(message = "Estado é obrigatório")
        @Size(min = 2, max = 2, message = "Estado deve conter a sigla UF com 2 caracteres")
        String estado) {
}