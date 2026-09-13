package com.jefiro.app247.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter @Setter @NoArgsConstructor
@Entity
@Table(name = "planograma", uniqueConstraints =
        @UniqueConstraint(name = "uk_planograma_empresa_nome", columnNames = {"empresa_id", "nome"}))
public class Planograma {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "char(36)", length = 36, nullable = false)
    private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false) private Empresa empresa;
    @Column(nullable = false, length = 150) private String nome;
    @Column(length = 600) private String descricao;
    @Column(nullable = false) private Boolean ativo = true;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @PrePersist void prePersist(){Instant now=Instant.now();if(createdAt==null)createdAt=now;updatedAt=now;if(ativo==null)ativo=true;}
    @PreUpdate void preUpdate(){updatedAt=Instant.now();}
}
