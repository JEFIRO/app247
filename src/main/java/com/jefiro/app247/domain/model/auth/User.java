package com.jefiro.app247.domain.model.auth;

import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.dto.UserRequestDTO;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor

@Table(name = "users")
@Entity

public class User implements UserDetails {

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
    @OneToMany(mappedBy = "user")
    private List<Order> orders;

    private String fotoPerfil;

    private Boolean ativo;
    private Boolean emailVerificado;
    @Enumerated(EnumType.STRING)
    private RoleUser role;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime ultimoLogin;

    public User(UserRequestDTO response) {
        this.uuidUser = UUID.randomUUID().toString();
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

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {

        if (this.role == RoleUser.ADMIN) {
            return List.of(
                    new SimpleGrantedAuthority("ROLE_ADMIN"),
                    new SimpleGrantedAuthority("ROLE_GERENTE"),
                    new SimpleGrantedAuthority("ROLE_USER")
            );
        }

        if (this.role == RoleUser.GERENTE) {
            return List.of(
                    new SimpleGrantedAuthority("ROLE_GERENTE"),
                    new SimpleGrantedAuthority("ROLE_USER")
            );
        }

        return List.of(
                new SimpleGrantedAuthority("ROLE_USER")
        );
    }

    @Override
    public @Nullable String getPassword() {
        return this.senha;
    }

    @Override
    public String getUsername() {
        return this.cpf;
    }

    @Override
    public boolean isAccountNonExpired() {
        return UserDetails.super.isAccountNonExpired();
    }

    @Override
    public boolean isAccountNonLocked() {
        return UserDetails.super.isAccountNonLocked();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return UserDetails.super.isCredentialsNonExpired();
    }

    @Override
    public boolean isEnabled() {
        return UserDetails.super.isEnabled();
    }
}