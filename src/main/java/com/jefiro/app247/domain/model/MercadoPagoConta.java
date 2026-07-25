package com.jefiro.app247.domain.model;


import com.jefiro.app247.domain.model.dto.MercadoPagoTokenResponse;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MercadoPagoConta {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36)
    private String idMercadoConta;

    @ManyToOne
    @JoinColumn(name = "empresa_id")
    private Empresa empresa;

    @Column(length = 500, nullable = false)
    private String accessToken;

    @Column(length = 500, nullable = false)
    private String refreshToken;

    @Column(length = 500)
    private String publicKey;

    @Column(nullable = false)
    private String mpUserId;

    private String tokenType;

    @Column(columnDefinition = "TEXT")
    private String scope;

    private Boolean liveMode;

    @Column(nullable = false)
    private LocalDateTime dataCriacao;

    @Column(nullable = false)
    private LocalDateTime dataExpiracao;

    private String terminalId;

    public MercadoPagoConta(MercadoPagoTokenResponse response) {
        LocalDateTime agora = LocalDateTime.now();
        this.accessToken = response.access_token();
        this.refreshToken = response.refresh_token();
        this.publicKey = response.public_key();
        this.mpUserId = response.user_id();
        this.tokenType = response.token_type();
        this.scope = response.scope();
        this.liveMode = response.live_mode();
        this.dataCriacao = agora;
        this.dataExpiracao = agora.plusSeconds(response.expires_in());
    }

    @PrePersist
    public void prePersist() {
        this.dataCriacao = LocalDateTime.now();
    }
}