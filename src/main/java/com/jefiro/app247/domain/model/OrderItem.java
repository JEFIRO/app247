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
        validarFonteDoSnapshot(order, item);
        Produto produto = item.getProduto();
        OrderItem s = new OrderItem();
        s.order = order;
        s.empresa = order.getEmpresa();
        s.produto = produto;
        s.promocao = item.getPromocao();
        s.codigoInterno = produto.getCodigoInterno();
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

    private static void validarFonteDoSnapshot(Order order, CartItem item) {
        if (order == null || order.getEmpresa() == null) {
            throw new IllegalStateException("Order sem empresa para gerar snapshot dos itens");
        }
        if (item == null || item.getProduto() == null) {
            throw new IllegalStateException("CartItem sem produto para gerar snapshot da venda");
        }
        Produto produto = item.getProduto();
        if (produto.getCodigoInterno() == null || produto.getCodigoInterno().isBlank()) {
            throw new IllegalStateException("Produto sem código interno para gerar snapshot da venda");
        }
        if (item.getName() == null || item.getName().isBlank()
                || item.getUnidadeMedida() == null
                || item.getQuantity() == null || item.getQuantity().signum() <= 0
                || item.getOriginalPrice() == null
                || item.getUnitPrice() == null
                || item.getCalculatedDiscount() == null
                || item.getCalculatedSubtotal() == null) {
            throw new IllegalStateException("CartItem possui dados obrigatórios incompletos para o snapshot da venda");
        }
    }

    public String getIdItem() {
        return id;
    }

    @PrePersist
    void prePersist() {
        if (codigoInterno == null || codigoInterno.isBlank()) {
            String codigoInternoProduto = produto != null ? produto.getCodigoInterno() : null;
            if (codigoInternoProduto == null || codigoInternoProduto.isBlank()) {
                throw new IllegalStateException(
                        "OrderItem sem código interno e sem produto válido para recuperar o snapshot");
            }
            codigoInterno = codigoInternoProduto;
        }
        if (createdAt == null) createdAt = Instant.now();
    }
    public BigDecimal getPrecoUnitarioAplicado() {
        return precoUnitarioAplicado;
    }
}
