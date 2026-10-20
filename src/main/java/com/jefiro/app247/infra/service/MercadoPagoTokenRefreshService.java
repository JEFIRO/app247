package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.MercadoPagoConta;
import com.jefiro.app247.domain.model.dto.MercadoPagoTokenResponse;
import com.jefiro.app247.domain.model.enum_type.MercadoPagoAccountBindingStatus;
import com.jefiro.app247.infra.exception.ApiBusinessException;
import com.jefiro.app247.infra.repository.OauthMercadoPagoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Renova o access token OAuth do Mercado Pago antes de vencer (~180 dias). Sem isso a conta
 * passa a responder {@code MERCADO_PAGO_NOT_CONFIGURED} e a Empresa para de cobrar até um
 * gestor refazer a autorização.
 *
 * <p>Cada conta é renovada em transação própria com lock pessimista na linha: o Mercado Pago
 * invalida o refresh token usado, então duas renovações concorrentes fariam uma perder a
 * credencial da outra. A falha fica em {@code last_error} e no AuditLog; o próximo ciclo
 * tenta de novo enquanto o token não vence.
 */
@Service
public class MercadoPagoTokenRefreshService {
    private static final Logger log = LoggerFactory.getLogger(MercadoPagoTokenRefreshService.class);

    private final OauthMercadoPagoRepository contas;
    private final OauthMercadoPagoService oauth;
    private final AuditLogService auditLogService;
    private final TransactionTemplate transaction;
    private final boolean enabled;
    private final Duration antecedencia;

    public MercadoPagoTokenRefreshService(
            OauthMercadoPagoRepository contas,
            OauthMercadoPagoService oauth,
            AuditLogService auditLogService,
            PlatformTransactionManager transactionManager,
            @Value("${mercado-pago.token-refresh.enabled:true}") boolean enabled,
            @Value("${mercado-pago.token-refresh.before-expiry:P30D}") Duration antecedencia) {
        this.contas = contas;
        this.oauth = oauth;
        this.auditLogService = auditLogService;
        this.transaction = new TransactionTemplate(transactionManager);
        this.enabled = enabled;
        this.antecedencia = antecedencia;
    }

    @Scheduled(
            fixedDelayString = "${mercado-pago.token-refresh.fixed-delay-ms:21600000}",
            initialDelayString = "${mercado-pago.token-refresh.initial-delay-ms:120000}")
    public void renovarAgendado() {
        if (!enabled) return;
        try {
            int renovadas = renovarExpirando();
            if (renovadas > 0) log.info("[MP-TOKEN-REFRESH] {} conta(s) renovada(s)", renovadas);
        } catch (RuntimeException error) {
            log.warn("[MP-TOKEN-REFRESH] ciclo falhou; o próximo tentará de novo", error);
        }
    }

    /** @return quantidade de contas renovadas neste ciclo */
    public int renovarExpirando() {
        Instant limite = Instant.now().plus(antecedencia);
        int renovadas = 0;
        for (String id : contas.findIdsParaRenovar(MercadoPagoAccountBindingStatus.ACTIVE, limite)) {
            try {
                if (renovar(id, limite)) renovadas++;
            } catch (RuntimeException error) {
                log.warn("[MP-TOKEN-REFRESH] falha inesperada bindingId={}", id, error);
            }
        }
        return renovadas;
    }

    boolean renovar(String contaId, Instant limite) {
        return Boolean.TRUE.equals(transaction.execute(status -> {
            MercadoPagoConta conta = contas.findByIdForUpdate(contaId).orElse(null);
            // Revalida sob lock: outra execução pode ter renovado ou a conta pode ter sido desvinculada.
            if (conta == null || !conta.isActive() || conta.getRefreshToken() == null
                    || conta.getRefreshToken().isBlank() || conta.getDataExpiracao() == null
                    || conta.getDataExpiracao().isAfter(limite)) {
                return false;
            }
            Instant expiravaEm = conta.getDataExpiracao();
            MercadoPagoTokenResponse token;
            try {
                token = oauth.renovarToken(conta.getRefreshToken());
            } catch (ApiBusinessException rejected) {
                return falhou(conta, "REFRESH_REJECTED", rejected.getMessage());
            } catch (RuntimeException transientFailure) {
                log.warn("[MP-TOKEN-REFRESH] Mercado Pago indisponível bindingId={}", contaId, transientFailure);
                return falhou(conta, "REFRESH_FAILED", "Falha de comunicação com o Mercado Pago");
            }
            if (token == null || !conta.getMpUserId().equals(token.user_id())) {
                return falhou(conta, "REFRESH_ACCOUNT_MISMATCH", "Renovação retornou outra conta Mercado Pago");
            }
            try {
                conta.atualizarCredenciais(token);
            } catch (IllegalArgumentException invalid) {
                return falhou(conta, "REFRESH_INVALID_RESPONSE", invalid.getMessage());
            }
            contas.save(conta);
            auditLogService.record(conta.getEmpresa(), "MERCADO_PAGO_TOKEN_REFRESHED", "MercadoPagoConta",
                    conta.getIdMercadoConta(), Map.of("expiresAt", expiravaEm.toString()),
                    Map.of("expiresAt", conta.getDataExpiracao().toString()), Map.of());
            return true;
        }));
    }

    private boolean falhou(MercadoPagoConta conta, String code, String detalhe) {
        String lastError = code + ": " + detalhe;
        conta.setLastError(lastError.length() > 300 ? lastError.substring(0, 300) : lastError);
        contas.save(conta);
        auditLogService.record(conta.getEmpresa(), "MERCADO_PAGO_TOKEN_REFRESH_FAILED", "MercadoPagoConta",
                conta.getIdMercadoConta(), null, null,
                Map.of("reason", code, "expiresAt", conta.getDataExpiracao().toString()));
        log.warn("[MP-TOKEN-REFRESH] bindingId={} reason={} expiresAt={}",
                conta.getIdMercadoConta(), code, conta.getDataExpiracao());
        return false;
    }
}
