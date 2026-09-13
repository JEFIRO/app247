package com.jefiro.app247.domain.model;

import com.jefiro.app247.domain.model.dto.EmpresaRequest;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "empresa")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Empresa {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "char(36)", length = 36, nullable = false)
    private String id;

    @Column(name = "razao_social", nullable = false, length = 150)
    private String razaoSocial;
    @Column(name = "nome_fantasia", nullable = false, length = 150)
    private String nomeFantasia;
    @Column(nullable = false, unique = true, length = 18)
    private String cnpj;
    @Column(nullable = false, unique = true, length = 180)
    private String email;
    @Column(length = 20)
    private String telefone;
    @Column(length = 9)
    private String cep;
    @Column(length = 180)
    private String logradouro;
    @Column(length = 20)
    private String numero;
    @Column(length = 100)
    private String bairro;
    @Column(length = 100)
    private String cidade;
    @Column(length = 2)
    private String estado;
    @Column(name = "nome_exibicao", length = 150)
    private String nomeExibicao;
    @Column(name = "logo_url", length = 500)
    private String logoUrl;
    @Column(name = "logo_dark_url", length = 500)
    private String logoDarkUrl;
    @Builder.Default
    @Column(name = "cor_principal", nullable = false, length = 7)
    private String corPrincipal = "#169DFF";
    @Builder.Default
    @Column(name = "cor_secundaria", nullable = false, length = 7)
    private String corSecundaria = "#62C8FF";
    @Builder.Default
    @Column(name = "cor_destaque", nullable = false, length = 7)
    private String corDestaque = "#00D084";
    @Column(name = "tenant_id", nullable = false, unique = true, columnDefinition = "char(36)", length = 36)
    private String tenantId;
    @Builder.Default
    @Column(nullable = false)
    private Boolean ativo = true;
    @Column(name = "closed_at")
    private Instant encerradaEm;
    @Column(name = "created_at", nullable = false)
    private Instant dataCadastro;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (tenantId == null) tenantId = UUID.randomUUID().toString();
        if (dataCadastro == null) dataCadastro = now;
        updatedAt = now;
        if (ativo == null) ativo = true;
        if (corPrincipal == null) corPrincipal = "#169DFF";
        if (corSecundaria == null) corSecundaria = "#62C8FF";
        if (corDestaque == null) corDestaque = "#00D084";
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public Empresa(EmpresaRequest request) {
        this.razaoSocial = request.razaoSocial();
        this.nomeFantasia = request.nomeFantasia();
        this.cnpj = request.cnpj();
        this.email = request.email();
        this.telefone = request.telefone();
        this.cep = request.cep();
        this.logradouro = request.logradouro();
        this.numero = request.numero();
        this.bairro = request.bairro();
        this.cidade = request.cidade();
        this.estado = request.estado();
        this.ativo = true;
    }

    public boolean isDefinitivamenteEncerrada() {
        return encerradaEm != null;
    }
}
