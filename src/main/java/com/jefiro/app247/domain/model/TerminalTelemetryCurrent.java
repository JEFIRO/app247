package com.jefiro.app247.domain.model;

import com.jefiro.app247.domain.model.enum_type.TerminalOperationalStatus;
import com.jefiro.app247.domain.model.terminal.Terminal;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "terminal_telemetry_current")
@Getter @Setter @NoArgsConstructor
public class TerminalTelemetryCurrent {
    @Id @Column(name = "terminal_id", columnDefinition = "char(36)", length = 36) private String terminalId;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @MapsId
    @JoinColumn(name = "terminal_id") private Terminal terminal;
    @Column(nullable = false) private Instant capturedAt;
    @Column(nullable = false) private Instant receivedAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private TerminalOperationalStatus operationalStatus;
    @Column(length = 1000) private String statusReasons;
    @Embedded private TerminalTelemetryMetrics metrics = new TerminalTelemetryMetrics();
}
