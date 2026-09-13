package com.jefiro.app247.domain.model;

import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.enum_type.OriginRequest;
import com.jefiro.app247.domain.model.enum_type.order.OrderStatus;
import com.jefiro.app247.domain.model.enum_type.order.StatusDetail;
import com.jefiro.app247.domain.model.terminal.Terminal;
import com.jefiro.app247.infra.service.MoneyPolicy;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "char(36)", length = 36, nullable = false)
    private String idOrder;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "condominio_id", nullable = false)
    private Condominio condominio;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "terminal_id", nullable = false)
    private Terminal terminal;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "carrinho_id", nullable = false, unique = true)
    private Carrinho carrinho;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    @OrderBy("createdAt ASC")
    private List<OrderItem> items = new ArrayList<>();
    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
    @OrderBy("attemptNumber ASC")
    private List<PaymentAttempt> paymentAttempts = new ArrayList<>();
    @Column(nullable = false, precision = 15, scale = 6)
    private BigDecimal subtotal;
    @Column(nullable = false, precision = 15, scale = 6)
    private BigDecimal desconto;
    @Column(name = "total_calculado", nullable = false, precision = 15, scale = 6)
    private BigDecimal totalCalculado;
    @Column(name = "total_cobrado", nullable = false, precision = 15, scale = 6)
    private BigDecimal totalCobrado;
    @Enumerated(EnumType.STRING)
    @Column(name = "origin_request", nullable = false, length = 30)
    private OriginRequest originRequest;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status = OrderStatus.PENDING;
    @Version
    @Column(nullable = false)
    private Long version = 0L;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "paid_at")
    private Instant paidAt;
    @Transient
    private String detachedTerminalId;

    public Order(Carrinho carrinho) {
        this.carrinho = carrinho;
        empresa = carrinho.getEmpresa();
        condominio = carrinho.getCondominio();
        terminal = carrinho.getTerminal();
        originRequest = OriginRequest.TERMINAL;
        atualizarTotaisDoCarrinho();
        snapshotItens();
    }

    public Order(Carrinho carrinho, User user) {
        this(carrinho);
        this.user = user;
    }

    public void snapshotItens() {
        if (carrinho == null || carrinho.getItems() == null || !items.isEmpty()) return;
        for (CartItem item : carrinho.getItems()) items.add(OrderItem.snapshot(this, item));
    }

    public void atualizarTotaisDoCarrinho() {
        if (carrinho == null) return;
        subtotal = MoneyPolicy.persistence(carrinho.getItems().stream().map(i -> i.getOriginalPrice().multiply(i.getQuantity())).reduce(BigDecimal.ZERO, BigDecimal::add));
        totalCalculado = MoneyPolicy.persistence(carrinho.getSubtotal());
        totalCobrado = MoneyPolicy.chargedForPersistence(totalCalculado);
        desconto = MoneyPolicy.persistence(subtotal.subtract(totalCalculado));
    }

    public BigDecimal getTotal() {
        return totalCobrado;
    }

    public void setTotal(BigDecimal total) {
        this.totalCobrado = total;
    }

    public String getIdTerminal() {
        return terminal != null ? terminal.getIdTerminal() : detachedTerminalId;
    }

    public void setIdTerminal(String value) {
        detachedTerminalId = value;
    }

    public PaymentAttempt getPagamento() {
        return paymentAttempts == null ? null : paymentAttempts.stream().max(Comparator.comparing(PaymentAttempt::getAttemptNumber, Comparator.nullsFirst(Integer::compareTo))).orElse(null);
    }

    public void setPagamento(PaymentAttempt attempt) {
        if (attempt == null) return;
        if (paymentAttempts == null) paymentAttempts = new ArrayList<>();
        attempt.setOrder(this);
        if (attempt.getEmpresa() == null) attempt.setEmpresa(empresa);
        if (!paymentAttempts.contains(attempt)) paymentAttempts.add(attempt);
    }

    public String getMpOrderId() {
        PaymentAttempt p = getPagamento();
        return p != null ? p.getProviderOrderId() : null;
    }

    public void setMpOrderId(String value) {
        requireAttempt().setProviderOrderId(value);
    }

    public String getMpType() {
        return "point";
    }

    public void setMpType(String ignored) {
    }

    public String getMpUserId() {
        PaymentAttempt p = getPagamento();
        return p != null ? p.getProviderUserId() : null;
    }

    public void setMpUserId(String value) {
        requireAttempt().setProviderUserId(value);
    }

    public OrderStatus getMpStatus() {
        return status;
    }

    public void setMpStatus(OrderStatus value) {
        status = value;
    }

    public StatusDetail getMpStatusDetail() {
        PaymentAttempt p = getPagamento();
        return p == null ? null : StatusDetail.findByValue(p.getStatusDetail());
    }

    public void setMpStatusDetail(StatusDetail value) {
        requireAttempt().setStatusDetail(value != null ? value.getValue() : null);
    }

    public String getMpTerminalId() {
        PaymentAttempt p = getPagamento();
        return p != null ? p.getProviderTerminalId() : null;
    }

    public void setMpTerminalId(String value) {
        requireAttempt().setProviderTerminalId(value);
    }

    public Integer getMpEventVersion() {
        PaymentAttempt p = getPagamento();
        return p != null ? p.getProviderEventVersion() : null;
    }

    public void setMpEventVersion(Integer value) {
        requireAttempt().setProviderEventVersion(value);
    }

    public Instant getMpEventDate() {
        PaymentAttempt p = getPagamento();
        return p == null ? null : p.getProviderEventAt();
    }

    public void setMpEventDate(Instant value) {
        requireAttempt().setProviderEventAt(value);
    }

    private PaymentAttempt requireAttempt() {
        PaymentAttempt p = getPagamento();
        if (p == null) throw new IllegalStateException("Order sem tentativa de pagamento");
        return p;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (status == null) status = OrderStatus.PENDING;
        snapshotItens();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
