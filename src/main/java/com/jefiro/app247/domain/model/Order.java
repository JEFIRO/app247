package com.jefiro.app247.domain.model;

import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.enum_type.OrderStatus;
import com.jefiro.app247.domain.model.enum_type.OriginRequest;
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
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36)
    private String idOrder;
    @ManyToOne
    @JoinColumn(name = "empresa_id")
    private Empresa empresa;
    @OneToOne
    @JoinColumn(name = "id_carrinho", nullable = false)
    private Carrinho carrinho;

    @ManyToOne
    @JoinColumn(name = "id_user")
    private User user;

    private String idTerminal;

    private BigDecimal subtotal;

    private BigDecimal desconto;

    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    private OriginRequest originRequest;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime paidAt;

    @OneToOne(mappedBy = "order", cascade = CascadeType.ALL)
    private Pagamento pagamento;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();

        if (status == null) {
            status = OrderStatus.PENDING;
        }

        if (subtotal == null && carrinho != null) {
            subtotal = carrinho.getSubtotal();
        }

        if (total == null) {
            total = subtotal;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Order(Carrinho carrinho, User user) {
        this.carrinho = carrinho;
        this.user = user;
        this.subtotal = carrinho.getSubtotal();
        this.total = carrinho.getSubtotal();
        this.status = OrderStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public Order(Carrinho carrinho) {
        this.createdAt = LocalDateTime.now();
        this.status = OrderStatus.PENDING;
        this.carrinho = carrinho;
        this.total = carrinho.getSubtotal();
        this.idTerminal = carrinho.getIdTerminal();
        this.setOriginRequest(OriginRequest.TERMINAL);
    }
}