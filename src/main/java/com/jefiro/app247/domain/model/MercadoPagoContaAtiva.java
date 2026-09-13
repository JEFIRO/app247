package com.jefiro.app247.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import org.springframework.data.domain.Persistable;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "mercado_pago_conta_ativa", uniqueConstraints = {
        @UniqueConstraint(name = "uk_mp_conta_ativa_empresa", columnNames = "empresa_id"),
        @UniqueConstraint(name = "uk_mp_conta_ativa_binding", columnNames = "mercado_pago_conta_id")
})
public class MercadoPagoContaAtiva implements Persistable<String> {
    @Id
    @Column(name = "mp_user_id", length = 120, nullable = false)
    private String mpUserId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false, unique = true)
    private Empresa empresa;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mercado_pago_conta_id", nullable = false, unique = true)
    private MercadoPagoConta conta;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Transient
    private boolean newEntity = true;

    public MercadoPagoContaAtiva(MercadoPagoConta conta) {
        this.mpUserId = conta.getMpUserId();
        this.empresa = conta.getEmpresa();
        this.conta = conta;
    }

    /**
     * O ID desta projeção é fornecido pelo provedor. Informar explicitamente ao Spring Data
     * que uma instância recém-criada deve usar persist/INSERT evita que merge transforme uma
     * disputa pela mesma conta em UPDATE e troque silenciosamente a Empresa proprietária.
     */
    @Override
    public String getId() {
        return mpUserId;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        newEntity = false;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
