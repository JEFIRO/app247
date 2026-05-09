package com.jefiro.app247.domain.model.auth;

import com.jefiro.app247.domain.model.dto.UserRequestDTO;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter

public class User {

    private String uuidUser;
    private Long userId;

    private String nome;
    private String sobrenome;

    private String email;
    private String senha;

    private String cpf;
    private String telefone;

    private LocalDate dataNascimento;

    private Endereco endereco;

    private String fotoPerfil;

    private Boolean ativo;
    private Boolean emailVerificado;

    private RoleUser role;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime ultimoLogin;

    public User(UserRequestDTO response) {
        this.nome = response.nome();
        this.sobrenome = response.sobrenome();
        this.email = response.email();
        this.senha = response.senha();
        this.cpf = response.cpf();
        this.telefone = response.telefone();
        this.dataNascimento = response.dataNascimento();
        this.ativo = true;
        this.emailVerificado = false;
        this.role = RoleUser.USER;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}