package com.jefiro.app247.domain.model.auth;

import com.jefiro.app247.domain.model.Condominio;
import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.dto.UserRequestDTO;
import jakarta.persistence.*;
import lombok.*;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity @Table(name = "users")
public class User implements UserDetails {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "char(36)", length = 36, nullable = false)
    private String idUser;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false) private Empresa empresa;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "condominio_id") private Condominio condominio;
    @Column(nullable = false, length = 100) private String nome;
    @Column(length = 100) private String sobrenome;
    @Column(nullable = false, unique = true, length = 180) private String email;
    @Column(nullable = false, length = 255) private String senha;
    @Column(nullable = false, unique = true, length = 14) private String cpf;
    @Column(length = 20) private String telefone;
    @Column(name = "data_nascimento") private LocalDate dataNascimento;
    @Column(name = "foto_perfil", length = 500) private String fotoPerfil;
    @Builder.Default @Column(nullable = false) private Boolean ativo = true;
    @Builder.Default @Column(name = "email_verificado", nullable = false) private Boolean emailVerificado = false;
    @Builder.Default @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private RoleUser role = RoleUser.USER;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "ultimo_login") private Instant ultimoLogin;

    public User(UserRequestDTO r) {
        nome=r.nome(); sobrenome=r.sobrenome(); email=r.email(); senha=r.senha(); cpf=r.cpf();
        telefone=r.telefone(); dataNascimento=r.dataNascimento(); ativo=true; emailVerificado=false;
        role=r.roleUser()!=null?r.roleUser():RoleUser.USER;
    }
    @PrePersist void prePersist(){ Instant now=Instant.now(); if(createdAt==null)createdAt=now; updatedAt=now;
        if(ativo==null)ativo=true; if(emailVerificado==null)emailVerificado=false; if(role==null)role=RoleUser.USER; }
    @PreUpdate void preUpdate(){ updatedAt=Instant.now(); }
    @Override public Collection<? extends GrantedAuthority> getAuthorities(){
        if(role==RoleUser.ADMIN)return List.of(new SimpleGrantedAuthority("ROLE_ADMIN"),new SimpleGrantedAuthority("ROLE_GERENTE"),new SimpleGrantedAuthority("ROLE_USER"));
        if(role==RoleUser.GERENTE)return List.of(new SimpleGrantedAuthority("ROLE_GERENTE"),new SimpleGrantedAuthority("ROLE_USER"));
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }
    @Override public @Nullable String getPassword(){return senha;}
    @Override public String getUsername(){return cpf;}
    @Override public boolean isEnabled(){return Boolean.TRUE.equals(ativo);}
}
