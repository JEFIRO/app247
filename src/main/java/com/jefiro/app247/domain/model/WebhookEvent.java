package com.jefiro.app247.domain.model;

import com.jefiro.app247.domain.model.enum_type.PaymentProvider;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name="webhook_event",uniqueConstraints=@UniqueConstraint(
        name="uk_webhook_provider_event",columnNames={"provider","external_event_id"}))
public class WebhookEvent {
    @Id @GeneratedValue(strategy=GenerationType.UUID)
    @Column(name="id",columnDefinition="char(36)",length=36,nullable=false) private String id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="empresa_id") private Empresa empresa;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="payment_attempt_id") private PaymentAttempt paymentAttempt;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private PaymentProvider provider=PaymentProvider.MERCADO_PAGO;
    @Column(name="external_event_id",nullable=false,length=160) private String eventId;
    @Column(nullable=false,length=100) private String action="UNKNOWN";
    @Column(name="provider_version") private Integer providerVersion;
    @Column(name="processing_status",nullable=false,length=30) private String processingStatus="RECEIVED";
    @Column(name="occurred_at") private Instant occurredAt;
    @Column(name="received_at",nullable=false) private Instant receivedAt;
    @Column(name="processed_at") private Instant processedAt;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="json") private String payload;
    @Column(name="processing_error",length=500) private String processingError;
    @PrePersist void prePersist(){if(receivedAt==null)receivedAt=Instant.now();}
}
