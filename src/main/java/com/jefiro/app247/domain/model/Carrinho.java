package com.jefiro.app247.domain.model;

import com.jefiro.app247.domain.model.enum_type.CarrinhoStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor

@Table(name = "carrinho")
@Entity
public class Carrinho {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String carrinhoId;

    @OneToMany(mappedBy = "carrinho",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    private List<Item> items = new ArrayList<>();

    private String terminalId;

    @Enumerated(EnumType.STRING)
    private CarrinhoStatus status;
    private BigDecimal subtotal;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Carrinho() {
        this.status = CarrinhoStatus.OPEN;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}