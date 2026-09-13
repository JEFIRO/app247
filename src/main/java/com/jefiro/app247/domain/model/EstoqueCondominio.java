package com.jefiro.app247.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "estoque_condominio", uniqueConstraints =
        @UniqueConstraint(name = "uk_estoque_condominio_produto", columnNames = {"condominio_id", "produto_id"}))
public class EstoqueCondominio {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "char(36)", length = 36, nullable = false) private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false) private Empresa empresa;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "condominio_id", nullable = false) private Condominio condominio;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false) private Produto produto;
    @Column(nullable = false, precision = 15, scale = 3) private BigDecimal quantidade = BigDecimal.ZERO;
    @Column(nullable = false) private Boolean ativo = true;
    @Version @Column(name = "lock_version", nullable = false) private Long lockVersion = 0L;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @PrePersist void prePersist(){Instant now=Instant.now();if(createdAt==null)createdAt=now;updatedAt=now;if(empresa==null&&condominio!=null)empresa=condominio.getEmpresa();}
    @PreUpdate void preUpdate(){updatedAt=Instant.now();}
    public void alterarDisponibilidade(boolean ativo){this.ativo=ativo;updatedAt=Instant.now();}
}
