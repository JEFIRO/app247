package com.jefiro.app247.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name="promocao_produto",uniqueConstraints=@UniqueConstraint(
        name="uk_promocao_produto",columnNames={"promocao_id","produto_id"}))
public class PromocaoProduto {
    @Id @GeneratedValue(strategy=GenerationType.UUID)
    @Column(name="id",columnDefinition="char(36)",length=36,nullable=false) private String id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="empresa_id",nullable=false) private Empresa empresa;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="promocao_id",nullable=false) private Promocao promocao;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="produto_id",nullable=false) private Produto produto;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @PrePersist void prePersist(){if(createdAt==null)createdAt=Instant.now();if(empresa==null&&promocao!=null)empresa=promocao.getEmpresa();}
}
