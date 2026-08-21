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

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false, unique = true)
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

    public MercadoPagoConta(MercadoPagoTokenResponse response) {
        atualizarCredenciais(response);
    }

    public void atualizarCredenciais(MercadoPagoTokenResponse response) {
        if (response == null || response.access_token() == null || response.access_token().isBlank()) {
            throw new IllegalArgumentException("Resposta OAuth do Mercado Pago sem access_token");
        }
        if (response.expires_in() == null || response.expires_in() <= 0) {
            throw new IllegalArgumentException("Resposta OAuth do Mercado Pago sem expires_in válido");
        }
        if ((this.refreshToken == null || this.refreshToken.isBlank())
                && (response.refresh_token() == null || response.refresh_token().isBlank())) {
            throw new IllegalArgumentException("Resposta OAuth do Mercado Pago sem refresh_token");
        }
        LocalDateTime agora = LocalDateTime.now();
        this.accessToken = response.access_token();
        if (response.refresh_token() != null && !response.refresh_token().isBlank()) {
            this.refreshToken = response.refresh_token();
        }
        if (response.public_key() != null) {
            this.publicKey = response.public_key();
        }
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
