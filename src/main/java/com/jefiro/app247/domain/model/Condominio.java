package com.jefiro.app247.domain.model;

import com.jefiro.app247.domain.model.auth.Endereco;
import com.jefiro.app247.domain.model.dto.CondominioRequest;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "condominio")
public class Condominio {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "char(36)", length = 36, nullable = false)
    private String idCondominio;
    @Column(nullable = false, length = 150)
    private String nome;
    @Column(length = 18)
    private String cnpj;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "endereco_id", unique = true)
    private Endereco endereco;
    @Column(nullable = false)
    private Boolean ativo = true;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Condominio(CondominioRequest request, Endereco endereco) {
        nome = request.nome();
        cnpj = request.cnpj();
        this.endereco = endereco;
        ativo = true;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
