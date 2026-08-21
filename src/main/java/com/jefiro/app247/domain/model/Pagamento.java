package com.jefiro.app247.domain.model;

import com.jefiro.app247.domain.model.dto.OrderResponse;
import com.jefiro.app247.domain.model.enum_type.PagamentoSource;
import com.jefiro.app247.domain.model.enum_type.PagamentoStatus;
import com.jefiro.app247.domain.model.enum_type.PagamentoTipo;
import com.jefiro.app247.domain.model.enum_type.PaymentMethodId;
import com.mercadopago.resources.payment.Payment;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter

@Table(name = "pagamento")
@Entity
@ToString
public class Pagamento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36)
    private String idPagamento;
    @ManyToOne
    @JoinColumn(name = "empresa_id")
    private Empresa empresa;
    @OneToOne(mappedBy = "pagamento")
    private Order order;
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    private PagamentoTipo tipo;

    @Enumerated(EnumType.STRING)
    private PagamentoStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PagamentoSource sourcePaiment;

    private Integer installments;

    private String transactionId;

    private String nsu;

    private String authorizationCode;

    private LocalDateTime paidAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Enumerated(EnumType.STRING)
    private PaymentMethodId paymentMethodId;

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

    public Pagamento(Order order, OrderResponse response) {
        this.sourcePaiment = PagamentoSource.TERMINAL;
        this.empresa = order.getEmpresa();
        this.order = order;
        this.valor = order.getTotal();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.status = PagamentoStatus.PENDING;
        if (response.transactions() != null
                && response.transactions().payments() != null
                && !response.transactions().payments().isEmpty()) {
            this.transactionId = response.transactions().payments().get(0).id();
        }
    }
    public Pagamento(Order order) {
        this.sourcePaiment = PagamentoSource.TERMINAL;
        this.empresa = order.getEmpresa();
        this.order = order;
        this.valor = order.getTotal();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.status = PagamentoStatus.PENDING;
    }
}
