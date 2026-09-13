package com.jefiro.app247.domain.model.terminal;

import com.jefiro.app247.domain.model.Condominio;
import com.jefiro.app247.domain.model.dto.TerminalRequest;
import com.jefiro.app247.domain.model.enum_type.TerminalStatus;
import com.jefiro.app247.domain.model.enum_type.TerminalLifecycleState;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter @Setter @NoArgsConstructor
@Entity(name = "terminal") @Table(name = "terminal")
public class Terminal {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "char(36)", length = 36, nullable = false)
    private String idTerminal;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "condominio_id", nullable = false) private Condominio condominio;
    @Column(nullable = false, length = 120) private String nome;
    @Column(nullable = false, length = 80) private String codigo;
    @Column(nullable = false) private Boolean ativo = true;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private TerminalStatus status;
    @Column(name = "last_ping") private Instant lastPing;
    @Column(name = "versao_software", length = 60) private String versaoSoftware;
    @Column(name = "serial_number", unique = true, length = 120) private String serialNumber;
    @Column(name = "mac_address", length = 30) private String macAddress;
    @Column(name = "ip_address", length = 45) private String ipAddress;
    @Column(name = "mercado_pago_terminal_id", unique = true, length = 120) private String mercadoPagoTerminalId;
    @Enumerated(EnumType.STRING) @Column(name = "lifecycle_state", nullable = false, length = 30)
    private TerminalLifecycleState lifecycleState = TerminalLifecycleState.ACTIVE;
    @Column(name = "reset_requested_at") private Instant resetRequestedAt;
    @Column(name = "reset_started_at") private Instant resetStartedAt;
    @Column(name = "reset_completed_at") private Instant resetCompletedAt;
    @Column(name = "reset_reason", length = 80) private String resetReason;
    @Column(name = "created_at", nullable = false) private Instant create_at;
    @Column(name = "updated_at", nullable = false) private Instant update_at;

    public Terminal(TerminalRequest r){nome=r.nome();codigo=r.nome();ativo=true;status=TerminalStatus.ONLINE;versaoSoftware="0.0.1";serialNumber=r.serialNumber();macAddress=r.macAddress();ipAddress=r.ipAddress();}
    @PrePersist void prePersist(){Instant now=Instant.now();if(create_at==null)create_at=now;update_at=now;if(status==null)status=TerminalStatus.OFFLINE;if(lifecycleState==null)lifecycleState=TerminalLifecycleState.ACTIVE;}
    @PreUpdate void preUpdate(){update_at=Instant.now();}
}
