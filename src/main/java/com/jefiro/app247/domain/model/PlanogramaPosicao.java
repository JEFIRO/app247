package com.jefiro.app247.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "planograma_posicao")
public class PlanogramaPosicao {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name="id", columnDefinition="char(36)", length=36, nullable=false) private String id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="empresa_id", nullable=false) private Empresa empresa;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="planograma_id", nullable=false) private Planograma planograma;
    @Column(length=100) private String setor;
    @Column(length=50) private String corredor;
    @Column(length=50) private String estante;
    @Column(length=50) private String modulo;
    @Column(length=50) private String prateleira;
    @Column(length=50) private String posicao;
    @Column(name="ordem_exibicao", nullable=false) private Integer ordem = 0;
    @Column(precision=12, scale=3) private BigDecimal x;
    @Column(precision=12, scale=3) private BigDecimal y;
    @Column(precision=12, scale=3) private BigDecimal largura;
    @Column(precision=12, scale=3) private BigDecimal altura;
    @Column(name="created_at", nullable=false, updatable=false) private Instant createdAt;
    @Column(name="updated_at", nullable=false) private Instant updatedAt;
    @PrePersist void prePersist(){Instant now=Instant.now();if(createdAt==null)createdAt=now;updatedAt=now;if(ordem==null)ordem=0;if(empresa==null&&planograma!=null)empresa=planograma.getEmpresa();}
    @PreUpdate void preUpdate(){updatedAt=Instant.now();}
}
