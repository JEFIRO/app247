package com.jefiro.app247.domain.model;

import com.jefiro.app247.domain.model.enum_type.PagamentoStatus;
import com.jefiro.app247.domain.model.enum_type.PaymentProvider;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name="payment_event",uniqueConstraints=@UniqueConstraint(
        name="uk_payment_event_provider",columnNames={"provider","provider_event_id"}))
public class PaymentEvent {
    @Id @GeneratedValue(strategy=GenerationType.UUID)
    @Column(name="id",columnDefinition="char(36)",length=36,nullable=false) private String id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="empresa_id",nullable=false) private Empresa empresa;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="payment_attempt_id",nullable=false) private PaymentAttempt paymentAttempt;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private PaymentProvider provider;
    @Column(name="event_type",nullable=false,length=80) private String eventType;
    @Enumerated(EnumType.STRING) @Column(name="status_anterior",length=40) private PagamentoStatus statusAnterior;
    @Enumerated(EnumType.STRING) @Column(name="status_novo",nullable=false,length=40) private PagamentoStatus statusNovo;
    @Column(name="provider_event_id",length=160) private String providerEventId;
    @Column(name="provider_version") private Integer providerVersion;
    @Column(name="occurred_at",nullable=false,updatable=false) private Instant occurredAt;
    @Column(name="received_at",nullable=false,updatable=false) private Instant receivedAt;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="json") private String metadata;
    @PrePersist void prePersist(){Instant now=Instant.now();if(occurredAt==null)occurredAt=now;if(receivedAt==null)receivedAt=now;if(empresa==null&&paymentAttempt!=null)empresa=paymentAttempt.getEmpresa();if(provider==null&&paymentAttempt!=null)provider=paymentAttempt.getProvider();}
}
