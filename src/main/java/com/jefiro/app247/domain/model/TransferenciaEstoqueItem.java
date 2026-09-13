package com.jefiro.app247.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name="transferencia_estoque_item",uniqueConstraints=
        @UniqueConstraint(name="uk_transferencia_item_produto",columnNames={"transferencia_id","produto_id"}))
public class TransferenciaEstoqueItem {
    @Id @GeneratedValue(strategy=GenerationType.UUID)
    @Column(name="id",columnDefinition="char(36)",length=36,nullable=false) private String id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="empresa_id",nullable=false) private Empresa empresa;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="transferencia_id",nullable=false) private TransferenciaEstoque transferencia;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="produto_id",nullable=false) private Produto produto;
    @Column(nullable=false,precision=15,scale=3) private BigDecimal quantidade;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @PrePersist void prePersist(){if(createdAt==null)createdAt=Instant.now();if(empresa==null&&transferencia!=null)empresa=transferencia.getEmpresa();}
}
