package com.jefiro.app247.domain.model;

import com.jefiro.app247.domain.model.enum_type.TelemetryAlertStatus;
import com.jefiro.app247.domain.model.enum_type.TelemetryAlertType;
import com.jefiro.app247.domain.model.terminal.Terminal;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "terminal_telemetry_alert", indexes = {
        @Index(name = "idx_telemetry_alert_terminal_opened", columnList = "terminal_id,opened_at"),
        @Index(name = "idx_telemetry_alert_status", columnList = "status")})
@Getter @Setter @NoArgsConstructor
public class TerminalTelemetryAlert {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "char(36)", length = 36, nullable = false) private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "terminal_id", nullable = false) private Terminal terminal;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private TelemetryAlertType type;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private TelemetryAlertStatus status;
    @Column(nullable = false, length = 300) private String message;
    @Column(nullable = false) private Instant openedAt;
    @Column(nullable = false) private Instant lastObservedAt;
    private Instant resolvedAt;
    @Column(name = "active_key", unique = true, length = 90) private String activeKey;
}
