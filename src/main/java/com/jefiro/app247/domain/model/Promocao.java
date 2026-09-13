package com.jefiro.app247.domain.model;

import com.jefiro.app247.domain.model.enum_type.AbrangenciaPromocao;
import com.jefiro.app247.domain.model.enum_type.StatusPromocao;
import com.jefiro.app247.domain.model.enum_type.TipoPromocao;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "promocao", indexes = {
        @Index(name="idx_promocao_empresa_periodo",columnList="empresa_id,ativo,inicio,fim"),
        @Index(name="idx_promocao_condominio_periodo",columnList="condominio_id,ativo,inicio,fim")})
public class Promocao {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name="id",columnDefinition="char(36)",length=36,nullable=false) private String idPromocao;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="empresa_id",nullable=false) private Empresa empresa;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="condominio_id") private Condominio condominio;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private AbrangenciaPromocao abrangencia;
    @Column(nullable=false,length=150) private String nome;
    @Column(length=500) private String descricao;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private TipoPromocao tipo;
    @Column(nullable=false,precision=15,scale=6) private BigDecimal valor;
    @Column(nullable=false) private Instant inicio;
    @Column(nullable=false) private Instant fim;
    @Column(nullable=false) private boolean ativo=true;
    @Column(nullable=false) private int prioridade;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @OneToMany(mappedBy="promocao",fetch=FetchType.LAZY,cascade={CascadeType.PERSIST,CascadeType.MERGE},orphanRemoval=true)
    private List<PromocaoProduto> produtos=new ArrayList<>();
    @PrePersist void prePersist(){Instant now=Instant.now();if(createdAt==null)createdAt=now;updatedAt=now;}
    @PreUpdate void preUpdate(){updatedAt=Instant.now();}
    public StatusPromocao statusEm(Instant now){if(!ativo)return StatusPromocao.DESATIVADA;if(now.isBefore(inicio))return StatusPromocao.AGENDADA;if(!now.isBefore(fim))return StatusPromocao.ENCERRADA;return StatusPromocao.ATIVA;}
    public void adicionarProduto(Produto produto){PromocaoProduto p=new PromocaoProduto();p.setPromocao(this);p.setProduto(produto);p.setEmpresa(empresa);produtos.add(p);}
    public List<Produto> produtosAssociados(){return produtos.stream().map(PromocaoProduto::getProduto).toList();}
}
