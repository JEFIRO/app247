package com.jefiro.app247.domain.model;

import com.jefiro.app247.domain.model.enum_type.TerminalPointBindingStatus;
import com.jefiro.app247.domain.model.terminal.Terminal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "terminal_point_binding")
public class TerminalPointBinding {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "char(36)", length = 36, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "terminal_id", nullable = false)
    private Terminal terminal;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mercado_pago_conta_id", nullable = false)
    private MercadoPagoConta mercadoPagoConta;

    @Column(name = "mercado_pago_terminal_id", nullable = false, length = 120)
    private String mercadoPagoTerminalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TerminalPointBindingStatus status;

    @Column(name = "linked_at", nullable = false)
    private Instant linkedAt;

    @Column(name = "unlinked_at")
    private Instant unlinkedAt;

    @Column(name = "unlink_reason", length = 80)
    private String unlinkReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public TerminalPointBinding(Terminal terminal, MercadoPagoConta conta,
                                String mercadoPagoTerminalId) {
        this.empresa = conta.getEmpresa();
        this.terminal = terminal;
        this.mercadoPagoConta = conta;
        this.mercadoPagoTerminalId = mercadoPagoTerminalId;
        this.status = TerminalPointBindingStatus.ACTIVE;
        this.linkedAt = Instant.now();
    }

    public void unlink(String reason) {
        if (status == TerminalPointBindingStatus.UNLINKED) return;
        status = TerminalPointBindingStatus.UNLINKED;
        unlinkedAt = Instant.now();
        unlinkReason = reason;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (linkedAt == null) linkedAt = now;
        if (createdAt == null) createdAt = now;
        if (status == null) status = TerminalPointBindingStatus.ACTIVE;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
