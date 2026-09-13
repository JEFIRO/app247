package com.jefiro.app247.domain.model;

import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.enum_type.StatusTransferenciaEstoque;
import com.jefiro.app247.domain.model.enum_type.TipoLocalEstoque;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name="transferencia_estoque")
public class TransferenciaEstoque {
    @Id @GeneratedValue(strategy=GenerationType.UUID)
    @Column(name="id",columnDefinition="char(36)",length=36,nullable=false) private String id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="empresa_id",nullable=false) private Empresa empresa;
    @Enumerated(EnumType.STRING) @Column(name="origem_tipo",nullable=false,length=30) private TipoLocalEstoque origemTipo;
    @Column(name="origem_id",columnDefinition="char(36)",length=36,nullable=false) private String origemId;
    @Enumerated(EnumType.STRING) @Column(name="destino_tipo",nullable=false,length=30) private TipoLocalEstoque destinoTipo;
    @Column(name="destino_id",columnDefinition="char(36)",length=36,nullable=false) private String destinoId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private StatusTransferenciaEstoque status=StatusTransferenciaEstoque.RASCUNHO;
    @Column(length=600) private String observacao;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="created_by") private User createdBy;
    @Version @Column(name="lock_version",nullable=false) private Long lockVersion;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @Column(name="confirmed_at") private Instant confirmedAt;
    @Column(name="cancelled_at") private Instant cancelledAt;
    @OneToMany(mappedBy="transferencia",fetch=FetchType.LAZY,cascade=CascadeType.PERSIST)
    private List<TransferenciaEstoqueItem> itens=new ArrayList<>();
    @PrePersist void prePersist(){Instant now=Instant.now();if(createdAt==null)createdAt=now;updatedAt=now;if(status==null)status=StatusTransferenciaEstoque.RASCUNHO;}
    @PreUpdate void preUpdate(){updatedAt=Instant.now();}
}
