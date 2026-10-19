package com.jefiro.app247.domain.model.dto;


import com.fasterxml.jackson.annotation.JsonFormat;
import com.jefiro.app247.domain.model.auth.Endereco;
import com.jefiro.app247.domain.model.auth.RoleUser;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record UserRequestDTO(

        @NotBlank(message = "Nome obrigatório")
        @Size(max = 100, message = "Nome deve ter no máximo 100 caracteres")
        String nome,

        @NotBlank(message = "Sobrenome obrigatório")
        @Size(max = 100, message = "Sobrenome deve ter no máximo 100 caracteres")
        String sobrenome,

        @Email(message = "Email inválido")
        @NotBlank(message = "Email obrigatório")
        @Size(max = 180, message = "Email deve ter no máximo 180 caracteres")
        String email,

        @NotBlank(message = "Senha obrigatória")
        @Size(min = 6, max = 72, message = "Senha deve ter entre 6 e 72 caracteres")
        String senha,

        @NotBlank(message = "CPF obrigatório")
        @Pattern(
                regexp = "\\d{11}",
                message = "CPF deve conter 11 dígitos numéricos"
        )
        String cpf,

        @Size(max = 20, message = "Telefone deve ter no máximo 20 caracteres")
        String telefone,

        @Past(message = "Data inválida")
        @JsonFormat(pattern = "dd/MM/yyyy")
        LocalDate dataNascimento,

        RoleUser roleUser

) {
}