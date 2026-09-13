package com.jefiro.app247.domain.model;

import com.jefiro.app247.domain.model.enum_type.LeadStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "marketing_lead")
public class MarketingLead {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "char(36)", length = 36, nullable = false)
    private String id;
    @Column(name = "nome", nullable = false, length = 100)
    private String nome;
    @Column(name = "empresa", length = 120)
    private String empresa;
    @Column(name = "email", nullable = false, length = 160)
    private String email;
    @Column(name = "telefone", nullable = false, length = 30)
    private String telefone;
    @Column(name = "cidade", length = 100)
    private String cidade;
    @Column(name = "estado", length = 40)
    private String estado;
    @Column(name = "quantidade_unidades")
    private Integer quantidadeUnidades;
    @Column(name = "quantidade_terminais")
    private Integer quantidadeTerminais;
    @Column(name = "ja_opera_mercado_autonomo", length = 30)
    private String jaOperaMercadoAutonomo;
    @Column(name = "mensagem", nullable = false, length = 2000)
    private String mensagem;
    @Column(name = "utm_source", length = 160)
    private String utmSource;
    @Column(name = "utm_medium", length = 160)
    private String utmMedium;
    @Column(name = "utm_campaign", length = 160)
    private String utmCampaign;
    @Column(name = "utm_content", length = 160)
    private String utmContent;
    @Column(name = "utm_term", length = 160)
    private String utmTerm;
    @Column(name = "landing_page", length = 500)
    private String landingPage;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private LeadStatus status;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (status == null) status = LeadStatus.NEW;
        if (createdAt == null) createdAt = Instant.now();
    }
}
