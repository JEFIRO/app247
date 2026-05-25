package com.jefiro.app247.domain.model;

import com.jefiro.app247.domain.model.enum_type.PagamentoStatus;
import com.jefiro.app247.domain.model.enum_type.PagamentoTipo;
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

    private LocalDateTime createdAt;

    private LocalDateTime paidAt;

    private LocalDateTime updatedAt;

    private String paymentMethodId;

    private String statusDetail;

    public Pagamento() {
        this.createdAt = LocalDateTime.now();
        this.status = PagamentoStatus.PENDING;
    }
}