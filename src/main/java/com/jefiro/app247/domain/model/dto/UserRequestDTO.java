package com.jefiro.app247.domain.model.dto;


import com.fasterxml.jackson.annotation.JsonFormat;
import com.jefiro.app247.domain.model.auth.Endereco;
import com.jefiro.app247.domain.model.auth.RoleUser;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record UserRequestDTO(

        @NotBlank(message = "Nome obrigatório")
        String nome,

        @NotBlank(message = "Sobrenome obrigatório")
        String sobrenome,

        @Email(message = "Email inválido")
        @NotBlank(message = "Email obrigatório")
        String email,

        @Size(min = 6, message = "Senha deve ter no mínimo 6 caracteres")
        String senha,

        @Pattern(
                regexp = "\\d{11}",
                message = "CPF deve conter 11 dígitos numéricos"
        )
        String cpf,

        String telefone,

        @Past(message = "Data inválida")
        @JsonFormat(pattern = "dd/MM/yyyy")
        LocalDate dataNascimento,

        RoleUser roleUser

) {
}