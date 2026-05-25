package com.jefiro.app247.domain.model;

import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.enum_type.OrderStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter

@AllArgsConstructor
@NoArgsConstructor

@Table(name = "orders")
@Entity
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String orderId;

    @OneToOne
    @JoinColumn(name = "carrinho_id")
    private Carrinho carrinho;

    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private LocalDateTime createdAt;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    public Order(Carrinho carrinho) {
        this.createdAt = LocalDateTime.now();
        this.status = OrderStatus.PENDING;
        this.carrinho = carrinho;
        this.total = carrinho.getSubtotal();
    }
}