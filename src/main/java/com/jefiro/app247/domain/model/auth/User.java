package com.jefiro.app247.domain.model.auth;

import com.jefiro.app247.domain.model.dto.UserRequestDTO;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor

@Table(name = "users")
@Entity

public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;
    private String uuidUser;

    private String nome;
    private String sobrenome;

    private String email;
    private String senha;

    private String cpf;
    private String telefone;

    private LocalDate dataNascimento;


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