package com.jefiro.app247.domain.model;

import com.jefiro.app247.domain.model.enum_type.ItemStatus;
import com.jefiro.app247.domain.model.enum_type.UnidadeMedida;
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

    private BigDecimal unitPrice;

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
        this.unitPrice = produto.getPreco();
        this.unidadeMedida = produto.getUnidadeMedida();
        this.expectedWeight = produto.getPeso();
        this.foto = produto.getFoto();
        this.quantity = quantity;
        this.receivedWeight = receivedWeight;
        this.status = ItemStatus.VALIDATED;
        this.requiresWeight = true;

    }

    public String getIdProduto() {
        return produto != null ? produto.getIdProduto() : null;
    }
}
