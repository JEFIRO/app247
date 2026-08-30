package com.jefiro.app247.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "estoque_condominio", uniqueConstraints = @UniqueConstraint(
        name = "uk_estoque_condominio_produto", columnNames = {"condominio_id", "produto_id"}))
public class EstoqueCondominio {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "condominio_id", nullable = false)
    private Condominio condominio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal quantidade = BigDecimal.ZERO;

    @Column(nullable = false)
    private Boolean ativo = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    private void prePersist() {
        LocalDateTime agora = LocalDateTime.now(ZoneOffset.UTC);
        if (createdAt == null) createdAt = agora;
        if (updatedAt == null) updatedAt = agora;
    }

    public void alterarDisponibilidade(boolean ativo) {
        this.ativo = ativo;
        this.updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
