package com.jefiro.app247.domain.model;

import com.jefiro.app247.domain.model.dto.OrderResponse;
import com.jefiro.app247.domain.model.enum_type.*;
import com.mercadopago.resources.payment.Payment;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "payment_attempt", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_attempt_number", columnNames = {"order_id", "attempt_number"}),
        @UniqueConstraint(name = "uk_payment_idempotency", columnNames = {"provider", "idempotency_key"}),
        @UniqueConstraint(name = "uk_payment_external_reference", columnNames = {"provider", "external_reference"}),
        @UniqueConstraint(name = "uk_payment_provider_order", columnNames = {"provider", "provider_order_id"}),
        @UniqueConstraint(name = "uk_payment_provider_payment", columnNames = {"provider", "provider_payment_id"})})
public class PaymentAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "char(36)", length = 36, nullable = false)
    private String idPagamento;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;
    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentProvider provider = PaymentProvider.MERCADO_PAGO;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentChannel channel = PaymentChannel.POINT;
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 50)
    private PagamentoTipo tipo;
    @Enumerated(EnumType.STRING)
    @Column(name = "provider_payment_method", length = 50)
    private PaymentMethodId paymentMethodId;
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 30)
    private PagamentoSource sourcePaiment = PagamentoSource.TERMINAL;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PagamentoStatus status = PagamentoStatus.PENDING;
    @Column(name = "amount_requested", nullable = false, precision = 15, scale = 6)
    private BigDecimal valor;
    @Column(name = "amount_approved", precision = 15, scale = 6)
    private BigDecimal amountApproved;
    private Integer installments;
    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;
    @Column(name = "external_reference", nullable = false, length = 120)
    private String externalReference;
    @Column(name = "provider_order_id", length = 120)
    private String providerOrderId;
    @Column(name = "provider_payment_id", length = 120)
    private String transactionId;
    @Column(name = "provider_terminal_id", length = 120)
    private String providerTerminalId;
    @Column(name = "provider_user_id", length = 120)
    private String providerUserId;
    @Column(name = "provider_event_version")
    private Integer providerEventVersion;
    @Column(name = "provider_event_at")
    private Instant providerEventAt;
    @Column(name = "status_detail", length = 160)
    private String statusDetail;
    @Column(length = 120)
    private String nsu;
    @Column(name = "authorization_code", length = 120)
    private String authorizationCode;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "approved_at")
    private Instant paidAt;
    @Column(name = "finished_at")
    private Instant finishedAt;

    public PaymentAttempt(Order order) {
        this.order = order;
        empresa = order.getEmpresa();
        valor = order.getTotalCobrado() != null ? order.getTotalCobrado() : order.getTotal();
        attemptNumber = order.getPaymentAttempts().size() + 1;
    }

    public PaymentAttempt(Order order, PagamentoTipo tipo, Payment payment) {
        this(order);
        this.tipo = tipo;
        if (payment != null && payment.getId() != null) transactionId = payment.getId().toString();
    }

    public PaymentAttempt(Order order, OrderResponse response) {
        this(order);
        if (response.transactions() != null && response.transactions().payments() != null && !response.transactions().payments().isEmpty())
            transactionId = response.transactions().payments().get(0).id();
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (attemptNumber == null) attemptNumber = 1;
        if (idempotencyKey == null) idempotencyKey = "attempt-" + UUID.randomUUID();
        if (externalReference == null)
            externalReference = (order != null ? order.getIdOrder() : "order") + ":" + attemptNumber;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
