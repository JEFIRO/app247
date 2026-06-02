package com.jefiro.app247.domain.model;

import com.jefiro.app247.domain.model.enum_type.PagamentoStatus;
import com.jefiro.app247.domain.model.enum_type.PagamentoTipo;
import com.mercadopago.resources.payment.Payment;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter

@Table(name = "pagamento")
@Entity
public class Pagamento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String pagamentoId;

    @OneToOne
    @JoinColumn(name = "order_id")
    private Order order;

    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    private PagamentoTipo tipo;

    @Enumerated(EnumType.STRING)
    private PagamentoStatus status;

    private String transactionId;

    private String nsu;

    private String authorizationCode;

    private LocalDateTime paidAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private String paymentMethodId;

    private String statusDetail;

    public Pagamento() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.status = PagamentoStatus.PENDING;
    }

    public Pagamento(Order order, PagamentoTipo tipo, Payment payment) {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.order = order;
        this.valor = order.getTotal();
        this.tipo = tipo;
        this.status = PagamentoStatus.PENDING;
        this.transactionId = payment.getId().toString();
    }


}