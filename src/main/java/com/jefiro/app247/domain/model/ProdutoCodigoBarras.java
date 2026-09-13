package com.jefiro.app247.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter @Setter @NoArgsConstructor
@Entity
@Table(name = "produto_codigo_barras", uniqueConstraints =
        @UniqueConstraint(name = "uk_barcode_empresa_codigo", columnNames = {"empresa_id", "codigo_barras"}))
public class ProdutoCodigoBarras {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "char(36)", length = 36, nullable = false) private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false) private Empresa empresa;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false) private Produto produto;
    @Column(name = "codigo_barras", nullable = false, length = 80) private String codigoBarras;
    @Column(nullable = false, length = 30) private String tipo = "INTERNO";
    @Column(nullable = false) private Boolean principal = false;
    @Column(nullable = false) private Boolean ativo = true;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @PrePersist void prePersist(){Instant now=Instant.now();if(createdAt==null)createdAt=now;updatedAt=now;if(empresa==null&&produto!=null)empresa=produto.getEmpresa();}
    @PreUpdate void preUpdate(){updatedAt=Instant.now();}
}
