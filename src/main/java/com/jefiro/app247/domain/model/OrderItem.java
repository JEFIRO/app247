package com.jefiro.app247.domain.model;

import com.jefiro.app247.domain.model.enum_type.TipoPromocao;
import com.jefiro.app247.domain.model.enum_type.UnidadeMedida;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "order_item", uniqueConstraints = @UniqueConstraint(
        name = "uk_order_item_produto", columnNames = {"order_id", "produto_id"}))
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "char(36)", length = 36, nullable = false)
    private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promocao_id")
    private Promocao promocao;
    @Column(name = "codigo_interno", nullable = false, length = 80)
    private String codigoInterno;
    @Column(name = "codigo_barras", length = 80)
    private String codigoBarras;
    @Column(nullable = false, length = 180)
    private String nome;
    @Enumerated(EnumType.STRING)
    @Column(name = "unidade_medida", nullable = false, length = 20)
    private UnidadeMedida unidadeMedida;
    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal quantidade;
    @Column(name = "preco_original", nullable = false, precision = 15, scale = 6)
    private BigDecimal precoOriginal;
    @Column(name = "preco_unitario_aplicado", nullable = false, precision = 15, scale = 6)
    private BigDecimal precoUnitarioAplicado;
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_promocao", length = 30)
    private TipoPromocao tipoPromocao;
    @Column(name = "valor_promocao", precision = 15, scale = 6)
    private BigDecimal valorPromocao;
    @Column(name = "desconto_calculado", nullable = false, precision = 15, scale = 6)
    private BigDecimal descontoCalculado;
    @Column(name = "subtotal_calculado", nullable = false, precision = 15, scale = 6)
    private BigDecimal subtotalCalculado;
    @Column(name = "fiscal_snapshot_version", length = 30)
    private String fiscalSnapshotVersion;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "fiscal_snapshot", columnDefinition = "json")
    private String fiscalSnapshot;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static OrderItem snapshot(Order order, CartItem item) {
        OrderItem s = new OrderItem();
        s.order = order;
        s.empresa = order.getEmpresa();
        s.produto = item.getProduto();
        s.promocao = item.getPromocao();
        s.codigoInterno = item.getProduto().getCodigoInterno();
        s.codigoBarras = item.getBarcode();
        s.nome = item.getName();
        s.unidadeMedida = item.getUnidadeMedida();
        s.quantidade = item.getQuantity();
        s.precoOriginal = item.getOriginalPrice();
        s.precoUnitarioAplicado = item.getUnitPrice();
        s.tipoPromocao = item.getPromotionType();
        s.valorPromocao = item.getPromotionValue();
        s.descontoCalculado = item.getCalculatedDiscount();
        s.subtotalCalculado = item.getCalculatedSubtotal();
        return s;
    }

    public String getIdItem() {
        return id;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
    public BigDecimal getPrecoUnitarioAplicado() {
        return precoUnitarioAplicado;
    }
}
