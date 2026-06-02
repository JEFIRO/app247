package com.jefiro.app247.domain.model.terminal;

import com.jefiro.app247.domain.model.Condominio;
import com.jefiro.app247.domain.model.auth.Endereco;
import com.jefiro.app247.domain.model.dto.TerminalRequest;
import com.jefiro.app247.domain.model.enum_type.TerminalStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data


@Entity(name = "terminal")
@Table(name = "terminal")
public class Terminal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long terminal_id;
    private String uuid_terminal;
    private String nome;
    private String codigo;
    private Boolean ativo;
    @Enumerated(value = EnumType.STRING)
    private TerminalStatus status;
    private LocalDateTime lastPing;
    @ManyToOne
    @JoinColumn(name = "condominio_id", nullable = false)
    private Condominio condominio;

    private String versaoSoftware;
    private String serialNumber;
    private String macAddress;
    private String ipAddress;

    private LocalDateTime create_at;
    private LocalDateTime update_at;

    public Terminal(TerminalRequest request) {
        this.uuid_terminal = UUID.randomUUID().toString();
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
