package com.jefiro.app247.domain.model;

import com.jefiro.app247.domain.model.dto.PrecoCalculado;
import com.jefiro.app247.domain.model.enum_type.ItemStatus;
import com.jefiro.app247.domain.model.enum_type.TipoPromocao;
import com.jefiro.app247.domain.model.enum_type.UnidadeMedida;
import com.jefiro.app247.infra.service.MoneyPolicy;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "cart_item", uniqueConstraints = @UniqueConstraint(
        name = "uk_cart_item_produto", columnNames = {"carrinho_id", "produto_id"}))
public class CartItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "char(36)", length = 36, nullable = false)
    private String idItem;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "carrinho_id", nullable = false)
    private Carrinho carrinho;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "codigo_barras_id")
    private ProdutoCodigoBarras codigoBarrasReferencia;
    @Column(name = "codigo_barras", length = 80)
    private String barcode;
    @Column(name = "nome", nullable = false, length = 180)
    private String name;
    @Column(length = 500)
    private String foto;
    @Column(name = "preco_unitario_aplicado", nullable = false, precision = 15, scale = 6)
    private BigDecimal unitPrice;
    @Column(name = "preco_original", nullable = false, precision = 15, scale = 6)
    private BigDecimal originalPrice;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promocao_id")
    private Promocao promocao;
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_promocao", length = 30)
    private TipoPromocao promotionType;
    @Column(name = "valor_promocao", precision = 15, scale = 6)
    private BigDecimal promotionValue;
    @Column(name = "desconto_calculado", nullable = false, precision = 15, scale = 6)
    private BigDecimal calculatedDiscount;
    @Column(name = "subtotal_calculado", nullable = false, precision = 15, scale = 6)
    private BigDecimal calculatedSubtotal;
    @Enumerated(EnumType.STRING)
    @Column(name = "unidade_medida", nullable = false, length = 20)
    private UnidadeMedida unidadeMedida;
    @Column(name = "quantidade", nullable = false, precision = 15, scale = 3)
    private BigDecimal quantity;
    @Column(name = "requer_peso", nullable = false)
    private Boolean requiresWeight = false;
    @Column(name = "peso_esperado", precision = 15, scale = 3)
    private BigDecimal expectedWeight;
    @Column(name = "peso_recebido", precision = 15, scale = 3)
    private BigDecimal receivedWeight;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ItemStatus status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public CartItem(Produto produto, Integer quantidade, BigDecimal pesoRecebido) {
        this(produto, new BigDecimal(quantidade), pesoRecebido);
    }

    public CartItem(Produto produto, BigDecimal quantidade, BigDecimal pesoRecebido) {
        this.produto = produto;
        empresa = produto.getEmpresa();
        barcode = produto.getCodigo();
        name = produto.getNome();
        foto = produto.getFoto();
        unitPrice = MoneyPolicy.persistence(produto.getPreco());
        originalPrice = unitPrice;
        unidadeMedida = produto.getUnidadeMedida();
        quantity = quantidade;
        expectedWeight = produto.getPeso();
        receivedWeight = pesoRecebido;
        status = ItemStatus.VALIDATED;
        calculatedDiscount = MoneyPolicy.persistence(BigDecimal.ZERO);
        calculatedSubtotal = MoneyPolicy.persistence(unitPrice.multiply(quantidade));
        requiresWeight = unidadeMedida != UnidadeMedida.UN;
    }

    public CartItem(PrecoCalculado preco, Integer quantidade, BigDecimal pesoRecebido) {
        this(preco, new BigDecimal(quantidade), pesoRecebido);
    }

    public CartItem(PrecoCalculado preco, BigDecimal quantidade, BigDecimal pesoRecebido) {
        this(preco.produto(), quantidade, pesoRecebido);
        originalPrice = preco.precoOriginal();
        unitPrice = preco.precoCalculado();
        calculatedDiscount = MoneyPolicy.persistence(originalPrice.subtract(unitPrice).multiply(quantidade));
        calculatedSubtotal = MoneyPolicy.persistence(unitPrice.multiply(quantidade));
        promocao = preco.promocao();
        if (promocao != null) {
            promotionType = promocao.getTipo();
            promotionValue = promocao.getValor();
        }
    }

    public String getIdProduto() {
        return produto != null ? produto.getIdProduto() : null;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (empresa == null && produto != null) empresa = produto.getEmpresa();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
