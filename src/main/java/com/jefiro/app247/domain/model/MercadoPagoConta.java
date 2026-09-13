package com.jefiro.app247.domain.model;

import com.jefiro.app247.domain.model.dto.MercadoPagoTokenResponse;
import com.jefiro.app247.domain.model.enum_type.MercadoPagoAccountBindingStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity @Table(name="mercado_pago_conta")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MercadoPagoConta {
    @Id @GeneratedValue(strategy=GenerationType.UUID)
    @Column(name="id",columnDefinition="char(36)",length=36,nullable=false) private String idMercadoConta;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="empresa_id",nullable=false) private Empresa empresa;
    @Column(name="access_token",length=1000) private String accessToken;
    @Column(name="refresh_token",length=1000) private String refreshToken;
    @Column(name="public_key",length=500) private String publicKey;
    @Column(name="mp_user_id",nullable=false,length=120) private String mpUserId;
    @Column(name="token_type",length=40) private String tokenType;
    @Column(columnDefinition="TEXT") private String scope;
    @Column(name="live_mode") private Boolean liveMode;
    @Column(name="token_created_at") private Instant dataCriacao;
    @Column(name="token_expires_at") private Instant dataExpiracao;
    @Builder.Default @Enumerated(EnumType.STRING) @Column(nullable=false,length=30)
    private MercadoPagoAccountBindingStatus status = MercadoPagoAccountBindingStatus.ACTIVE;
    @Column(name="linked_at",nullable=false) private Instant linkedAt;
    @Column(name="unlinked_at") private Instant unlinkedAt;
    @Column(name="unlink_reason",length=80) private String unlinkReason;
    @Column(name="last_error",length=300) private String lastError;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    public MercadoPagoConta(MercadoPagoTokenResponse response){atualizarCredenciais(response);}
    public void atualizarCredenciais(MercadoPagoTokenResponse r){
        if(r==null||r.access_token()==null||r.access_token().isBlank())throw new IllegalArgumentException("Resposta OAuth do Mercado Pago sem access_token");
        if(r.user_id()==null||r.user_id().isBlank())throw new IllegalArgumentException("Resposta OAuth do Mercado Pago sem user_id");
        if(r.expires_in()==null||r.expires_in()<=0)throw new IllegalArgumentException("Resposta OAuth do Mercado Pago sem expires_in válido");
        if((refreshToken==null||refreshToken.isBlank())&&(r.refresh_token()==null||r.refresh_token().isBlank()))throw new IllegalArgumentException("Resposta OAuth do Mercado Pago sem refresh_token");
        Instant now=Instant.now();accessToken=r.access_token();if(r.refresh_token()!=null&&!r.refresh_token().isBlank())refreshToken=r.refresh_token();
        if(r.public_key()!=null)publicKey=r.public_key();mpUserId=r.user_id();tokenType=r.token_type();scope=r.scope();liveMode=r.live_mode();dataCriacao=now;dataExpiracao=now.plusSeconds(r.expires_in());
        status=MercadoPagoAccountBindingStatus.ACTIVE;if(linkedAt==null)linkedAt=now;unlinkedAt=null;unlinkReason=null;lastError=null;
    }
    public void unlink(String reason){
        status=MercadoPagoAccountBindingStatus.UNLINKED;unlinkedAt=Instant.now();unlinkReason=reason;
        accessToken=null;refreshToken=null;publicKey=null;tokenType=null;scope=null;dataCriacao=null;dataExpiracao=null;
    }
    public boolean isActive(){return status==MercadoPagoAccountBindingStatus.ACTIVE;}
    @PrePersist void prePersist(){Instant now=Instant.now();if(dataCriacao==null&&accessToken!=null)dataCriacao=now;if(linkedAt==null)linkedAt=now;if(createdAt==null)createdAt=now;if(status==null)status=MercadoPagoAccountBindingStatus.ACTIVE;updatedAt=now;}
    @PreUpdate void preUpdate(){updatedAt=Instant.now();}
}
