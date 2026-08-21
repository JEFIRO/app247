package com.jefiro.app247.domain.model.auth;

import com.jefiro.app247.domain.model.Condominio;
import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.dto.UserRequestDTO;
import jakarta.persistence.*;
import lombok.*;
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
@Builder

@Table(name = "users")
@Entity

public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36)
    private String idUser;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

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

    @ManyToOne
    @JoinColumn(name = "id_condominio")
    private Condominio condominio;

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

        if (response.roleUser() != null) {
            this.role = response.roleUser();
        } else {
            this.role = RoleUser.USER;
        }

        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PrePersist
    public void prePersist() {
        if (idUser == null) {
            idUser = UUID.randomUUID().toString();
        }
        if (role == null) {
            role = RoleUser.USER;
        }
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
        return Boolean.TRUE.equals(ativo);
    }
}
