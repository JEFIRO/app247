package com.jefiro.app247.domain.model;

import com.jefiro.app247.domain.model.enum_type.TerminalOperationalStatus;
import com.jefiro.app247.domain.model.terminal.Terminal;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "terminal_telemetry_history", indexes = {
        @Index(name = "idx_telemetry_history_terminal_captured", columnList = "terminal_id,captured_at")})
@Getter @Setter @NoArgsConstructor
public class TerminalTelemetryHistory {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "char(36)", length = 36, nullable = false) private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "terminal_id", nullable = false) private Terminal terminal;
    @Column(nullable = false) private Instant capturedAt;
    @Column(nullable = false) private Instant receivedAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private TerminalOperationalStatus operationalStatus;
    @Embedded private TerminalTelemetryMetrics metrics = new TerminalTelemetryMetrics();
}
