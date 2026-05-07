package com.jefiro.app247.domain.model;

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
    private String itemId;

    @ManyToOne
    @JoinColumn(name = "carrinho_id")
    private Carrinho carrinho;

    private Long productId;

    private String barcode;

    private String name;

    private BigDecimal unitPrice;

    private Integer quantity;

    private Boolean requiresWeight;

    private BigDecimal expectedWeight;

    private BigDecimal receivedWeight;

    private String status;

    public Item(Produto produto, Integer quantity,
                BigDecimal receivedWeight) {
        this.productId = produto.getId();
        this.barcode = produto.getCodigo();
        this.name = produto.getNome();
        this.unitPrice = produto.getPreco();
        this.expectedWeight = produto.getPeso();
        this.quantity = quantity;
        this.receivedWeight = receivedWeight;

    }
}