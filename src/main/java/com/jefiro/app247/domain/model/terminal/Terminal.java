package com.jefiro.app247.domain.model.terminal;

import com.jefiro.app247.domain.model.Condominio;
import com.jefiro.app247.domain.model.dto.TerminalRequest;
import com.jefiro.app247.domain.model.enum_type.TerminalStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Data


@Entity(name = "terminal")
@Table(name = "terminal")
public class Terminal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36)
    private String idTerminal;
    private String nome;
    private String codigo;
    private Boolean ativo;
    @Enumerated(value = EnumType.STRING)
    private TerminalStatus status;
    private LocalDateTime lastPing;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "condominio_id", nullable = false)
    private Condominio condominio;

    private String versaoSoftware;
    private String serialNumber;
    private String macAddress;
    private String ipAddress;

    @Column(name = "mercado_pago_terminal_id", unique = true)
    private String mercadoPagoTerminalId;

    private LocalDateTime create_at;
    private LocalDateTime update_at;
    public Terminal(TerminalRequest request) {
        this.nome = request.nome();
        this.codigo = request.serialNumber();
        this.ativo = true;
        this.status = TerminalStatus.ONLINE;
        this.versaoSoftware = "0.0.1";
        this.serialNumber = request.serialNumber();
        this.macAddress = request.macAddress();
        this.ipAddress = request.ipAddress();
        this.create_at = LocalDateTime.now();
        this.update_at = LocalDateTime.now();
    }
}
