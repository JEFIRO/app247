package com.jefiro.app247.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "promocao_produto", uniqueConstraints = @UniqueConstraint(
        name = "uk_promocao_produto", columnNames = {"promocao_id", "produto_id"}), indexes = {
        @Index(name = "idx_promocao_produto_promocao", columnList = "promocao_id"),
        @Index(name = "idx_promocao_produto_produto", columnList = "produto_id")
})
public class PromocaoProduto {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "promocao_id", nullable = false)
    private Promocao promocao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
