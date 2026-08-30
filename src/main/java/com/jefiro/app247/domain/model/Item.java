package com.jefiro.app247.domain.model;

import com.jefiro.app247.domain.model.enum_type.ItemStatus;
import com.jefiro.app247.domain.model.enum_type.TipoPromocao;
import com.jefiro.app247.domain.model.enum_type.UnidadeMedida;
import com.jefiro.app247.domain.model.dto.PrecoCalculado;
import com.jefiro.app247.infra.service.MoneyPolicy;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor

@Table(name = "item")
@Entity
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36)
    private String idItem;
    @ManyToOne
    @JoinColumn(name = "empresa_id")
    private Empresa empresa;
    @ManyToOne
    @JoinColumn(name = "id_carrinho")
    private Carrinho carrinho;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_produto", nullable = false)
    private Produto produto;

    private String barcode;

    private String name;
    private String foto;

    @Column(nullable = false, precision = 15, scale = 6)
    private BigDecimal unitPrice;

    @Column(name = "original_price", nullable = false, precision = 15, scale = 6)
    private BigDecimal originalPrice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promocao_id")
    private Promocao promocao;

    @Enumerated(EnumType.STRING)
    @Column(name = "promotion_type", length = 30)
    private TipoPromocao promotionType;

    @Column(name = "promotion_value", precision = 15, scale = 6)
    private BigDecimal promotionValue;

    @Column(name = "calculated_discount", nullable = false, precision = 15, scale = 6)
    private BigDecimal calculatedDiscount;

    @Column(name = "calculated_subtotal", nullable = false, precision = 15, scale = 6)
    private BigDecimal calculatedSubtotal;

    @Enumerated(EnumType.STRING)
    private UnidadeMedida unidadeMedida;

    private Integer quantity;

    private Boolean requiresWeight;

    private BigDecimal expectedWeight;

    private BigDecimal receivedWeight;

    @Enumerated(EnumType.STRING)
    private ItemStatus status;

    public Item(Produto produto, Integer quantity,
                BigDecimal receivedWeight) {
        this.produto = produto;
        this.barcode = produto.getCodigo();
        this.name = produto.getNome();
        this.unitPrice = MoneyPolicy.persistence(produto.getPreco());
        this.originalPrice = this.unitPrice;
        this.calculatedDiscount = MoneyPolicy.persistence(BigDecimal.ZERO);
        this.calculatedSubtotal = MoneyPolicy.persistence(
                this.unitPrice.multiply(BigDecimal.valueOf(quantity)));
        this.unidadeMedida = produto.getUnidadeMedida();
        this.expectedWeight = produto.getPeso();
        this.foto = produto.getFoto();
        this.quantity = quantity;
        this.receivedWeight = receivedWeight;
        this.status = ItemStatus.VALIDATED;
        this.requiresWeight = true;

    }

    public Item(PrecoCalculado preco, Integer quantity, BigDecimal receivedWeight) {
        this(preco.produto(), quantity, receivedWeight);
        this.originalPrice = preco.precoOriginal();
        this.unitPrice = preco.precoCalculado();
        this.calculatedDiscount = preco.descontoCalculado();
        this.calculatedSubtotal = MoneyPolicy.persistence(preco.subtotal(quantity));
        this.promocao = preco.promocao();
        if (preco.promocao() != null) {
            this.promotionType = preco.promocao().getTipo();
            this.promotionValue = preco.promocao().getValor();
        }
    }

    public String getIdProduto() {
        return produto != null ? produto.getIdProduto() : null;
    }
}
