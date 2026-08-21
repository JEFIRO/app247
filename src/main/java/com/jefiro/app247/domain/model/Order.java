package com.jefiro.app247.domain.model;

import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.enum_type.order.OrderStatus;
import com.jefiro.app247.domain.model.enum_type.OriginRequest;
import com.jefiro.app247.domain.model.enum_type.order.StatusDetail;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "orders")
@ToString
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

    @Column(length = 36)
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

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "id_pagamento")
    private Pagamento pagamento;

    @Column(name = "mp_order_id")
    private String mpOrderId;

    @Column(name = "mp_type")
    private String mpType;

    @Column(name = "mp_user_id")
    private String mpUserId;

    @Column(name = "mp_status")
    @Enumerated(EnumType.STRING)
    private OrderStatus mpStatus;

    @Column(name = "mp_status_detail")
    @Enumerated(EnumType.STRING)
    private StatusDetail mpStatusDetail;

    @Column(name = "mp_terminal_id")
    private String mpTerminalId;

    @Column(name = "mp_event_version")
    private Integer mpEventVersion;

    @Column(name = "mp_event_date")
    private LocalDateTime mpEventDate;


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
    }

    public Order(Carrinho carrinho) {
        this.carrinho = carrinho;
        this.empresa = carrinho.getEmpresa();
        this.total = carrinho.getSubtotal();
        this.idTerminal = carrinho.getIdTerminal();
        this.setOriginRequest(OriginRequest.TERMINAL);
    }
}
