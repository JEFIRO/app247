package com.jefiro.app247.domain.model;

import com.jefiro.app247.domain.model.enum_type.TipoMovimentacaoEstoque;
import com.jefiro.app247.domain.model.auth.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name="movimentacao_estoque",uniqueConstraints=@UniqueConstraint(
        name="uk_movimento_idempotencia",columnNames={"empresa_id","chave_idempotencia"}))
public class MovimentacaoEstoque {
    @Id @GeneratedValue(strategy=GenerationType.UUID)
    @Column(name="id",columnDefinition="char(36)",length=36,nullable=false) private String id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="empresa_id",nullable=false) private Empresa empresa;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="estoque_condominio_id") private EstoqueCondominio estoque;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="estoque_empresa_id") private EstoqueEmpresa estoqueEmpresa;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=40) private TipoMovimentacaoEstoque tipo;
    @Column(nullable=false,precision=15,scale=3) private BigDecimal quantidade;
    @Column(name="saldo_anterior",nullable=false,precision=15,scale=3) private BigDecimal quantidadeAnterior;
    @Column(name="saldo_posterior",nullable=false,precision=15,scale=3) private BigDecimal quantidadePosterior;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="order_id") private Order order;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="order_item_id") private OrderItem item;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="transferencia_id") private TransferenciaEstoque transferencia;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="inventario_id") private Inventario inventario;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="inventario_item_id") private InventarioItem inventarioItem;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="created_by") private User createdBy;
    @Column(length=300) private String motivo;
    @Column(name="chave_idempotencia",nullable=false,length=160) private String chaveIdempotencia;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @PrePersist void prePersist(){
        if(createdAt==null)createdAt=Instant.now();
        if(estoque==null&&estoqueEmpresa==null)throw new IllegalStateException("Movimentação precisa de uma localização de estoque");
        if(estoque!=null&&estoqueEmpresa!=null)throw new IllegalStateException("Movimentação não pode apontar para duas localizações");
        if(empresa==null)empresa=estoque!=null?estoque.getEmpresa():estoqueEmpresa.getEmpresa();
    }
}
