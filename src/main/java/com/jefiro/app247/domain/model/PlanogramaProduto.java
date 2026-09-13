package com.jefiro.app247.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter @Setter @NoArgsConstructor
@Entity
@Table(name="planograma_produto", uniqueConstraints=
        @UniqueConstraint(name="uk_planograma_posicao_produto", columnNames={"planograma_posicao_id","produto_id"}))
public class PlanogramaProduto {
    @Id @GeneratedValue(strategy=GenerationType.UUID)
    @Column(name="id",columnDefinition="char(36)",length=36,nullable=false) private String id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="empresa_id",nullable=false) private Empresa empresa;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="planograma_posicao_id",nullable=false) private PlanogramaPosicao posicao;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="produto_id",nullable=false) private Produto produto;
    @Column(nullable=false) private Integer facings = 1;
    @Column(precision=15,scale=3) private BigDecimal capacidade;
    @Column(name="quantidade_ideal",precision=15,scale=3) private BigDecimal quantidadeIdeal;
    @Column(name="quantidade_minima",precision=15,scale=3) private BigDecimal quantidadeMinima;
    @Column(nullable=false) private Boolean ativo = true;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @PrePersist void prePersist(){Instant now=Instant.now();if(createdAt==null)createdAt=now;updatedAt=now;if(facings==null)facings=1;if(ativo==null)ativo=true;if(empresa==null&&posicao!=null)empresa=posicao.getEmpresa();}
    @PreUpdate void preUpdate(){updatedAt=Instant.now();}
}
