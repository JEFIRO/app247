package com.jefiro.app247.domain.model.dto;

import com.jefiro.app247.domain.model.auth.Endereco;
import com.jefiro.app247.domain.model.auth.RoleUser;
import com.jefiro.app247.domain.model.auth.User;

import java.time.LocalDate;
import java.time.Instant;

public record UserResponseDTO(
        String userId,
        String nome,
        String sobrenome,
        String email,
        String cpf,
        String telefone,
        LocalDate dataNascimento,
        String fotoPerfil,
        Boolean ativo,
        Boolean emailVerificado,
        RoleUser role,
        Instant createdAt,
        Instant updatedAt,
        Instant ultimoLogin
) {
    public UserResponseDTO(User user) {
        this(user.getIdUser(),
                user.getNome(),
                user.getSobrenome(),
                user.getEmail(),
                user.getCpf(),
                user.getTelefone(),
                user.getDataNascimento(),
                user.getFotoPerfil(),
                user.getAtivo(),
                user.getEmailVerificado(),
                user.getRole(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getUltimoLogin()
        );
    }
}
