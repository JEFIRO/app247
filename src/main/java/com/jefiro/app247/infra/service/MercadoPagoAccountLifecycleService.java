package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.MercadoPagoConta;
import com.jefiro.app247.domain.model.MercadoPagoContaAtiva;
import com.jefiro.app247.domain.model.dto.MercadoPagoTokenResponse;
import com.jefiro.app247.domain.model.enum_type.MercadoPagoAccountBindingStatus;
import com.jefiro.app247.domain.model.enum_type.PagamentoStatus;
import com.jefiro.app247.infra.exception.ApiBusinessException;
import com.jefiro.app247.infra.repository.EmpresaRepository;
import com.jefiro.app247.infra.repository.MercadoPagoContaAtivaRepository;
import com.jefiro.app247.infra.repository.OauthMercadoPagoRepository;
import com.jefiro.app247.infra.repository.PagamentoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class MercadoPagoAccountLifecycleService {
    private static final List<PagamentoStatus> ACTIVE_PAYMENT_STATUSES = List.of(
            PagamentoStatus.PENDING, PagamentoStatus.ACTION_REQUIRED);

    @Autowired
    EmpresaRepository empresaRepository;
    @Autowired
    OauthMercadoPagoRepository contaRepository;
    @Autowired
    MercadoPagoContaAtivaRepository ativaRepository;
    @Autowired
    PagamentoRepository pagamentoRepository;
    @Autowired
    TerminalPointBindingService pointBindingService;
    @Autowired
    AuditLogService auditLogService;

    @Transactional
    public AuthorizationResult autorizar(String empresaId, MercadoPagoTokenResponse token,
                                         boolean substituicaoConfirmada) {
        validarToken(token);
        Empresa empresa = empresaRepository.findByIdForUpdate(empresaId)
                .orElseThrow(() -> new ApiBusinessException(
                        HttpStatus.NOT_FOUND, "COMPANY_NOT_FOUND", "Empresa não encontrada"));
        validarEmpresaOperacional(empresa);

        Optional<MercadoPagoContaAtiva> atualOpt = ativaRepository.findByEmpresaIdForUpdate(empresaId);
        if (atualOpt.isPresent()) {
            MercadoPagoConta atual = atualOpt.get().getConta();
            if (atual.getMpUserId().equals(token.user_id())) {
                atual.atualizarCredenciais(token);
                contaRepository.save(atual);
                auditLogService.record(empresa, "MERCADO_PAGO_REAUTHORIZED", "MercadoPagoConta",
                        atual.getIdMercadoConta(), null,
                        Map.of("mpUserId", atual.getMpUserId()), Map.of());
                return new AuthorizationResult(atual, AuthorizationType.REAUTHORIZED);
            }
            if (!substituicaoConfirmada) {
                throw replacementRequired();
            }
            validarSemPagamentosAtivos(empresaId);
        }

        Optional<MercadoPagoContaAtiva> vinculada = ativaRepository
                .findWithBindingByMpUserId(token.user_id());
        if (vinculada.isPresent()
                && !vinculada.get().getEmpresa().getId().equals(empresaId)) {
            throw alreadyLinked();
        }

        boolean replacing = atualOpt.isPresent();
        MercadoPagoConta anterior = atualOpt.map(MercadoPagoContaAtiva::getConta).orElse(null);
        if (replacing) {
            pointBindingService.desvincularDaConta(anterior, "MERCADO_PAGO_ACCOUNT_REPLACED");
            anterior.unlink("REPLACED");
            contaRepository.save(anterior);
            ativaRepository.delete(atualOpt.get());
            ativaRepository.flush();
        }

        MercadoPagoConta nova = new MercadoPagoConta(token);
        nova.setEmpresa(empresa);
        nova = contaRepository.saveAndFlush(nova);
        ativaRepository.saveAndFlush(new MercadoPagoContaAtiva(nova));

        String action = replacing ? "MERCADO_PAGO_REPLACED" : "MERCADO_PAGO_LINKED";
        auditLogService.record(empresa, action, "MercadoPagoConta", nova.getIdMercadoConta(),
                anterior == null ? null : Map.of("bindingId", anterior.getIdMercadoConta()),
                Map.of("bindingId", nova.getIdMercadoConta(), "mpUserId", nova.getMpUserId()),
                Map.of());
        return new AuthorizationResult(nova,
                replacing ? AuthorizationType.REPLACED : AuthorizationType.LINKED);
    }

    @Transactional
    public AuthorizationResult reautorizarAposConflito(String empresaId,
                                                        MercadoPagoTokenResponse token) {
        MercadoPagoContaAtiva ativa = ativaRepository.findByEmpresaIdForUpdate(empresaId)
                .orElseThrow(this::alreadyLinked);
        if (!ativa.getMpUserId().equals(token.user_id())) {
            throw replacementRequired();
        }
        MercadoPagoConta conta = ativa.getConta();
        conta.atualizarCredenciais(token);
        contaRepository.save(conta);
        auditLogService.record(conta.getEmpresa(), "MERCADO_PAGO_REAUTHORIZED", "MercadoPagoConta",
                conta.getIdMercadoConta(), null, Map.of("mpUserId", conta.getMpUserId()), Map.of());
        return new AuthorizationResult(conta, AuthorizationType.REAUTHORIZED);
    }

    @Transactional
    public void desvincular(String empresaId) {
        Empresa empresa = empresaRepository.findByIdForUpdate(empresaId)
                .orElseThrow(() -> new ApiBusinessException(
                        HttpStatus.NOT_FOUND, "COMPANY_NOT_FOUND", "Empresa não encontrada"));
        validarSemPagamentosAtivos(empresaId);
        desvincularInterno(empresa, "USER_UNLINK");
    }

    @Transactional
    public void desvincularParaEncerramento(Empresa empresa) {
        desvincularInterno(empresa, "COMPANY_CLOSED");
    }

    @Transactional(readOnly = true)
    public MercadoPagoConta getAtivaPorEmpresa(String empresaId) {
        MercadoPagoConta conta = ativaRepository.findByEmpresaId(empresaId)
                .map(MercadoPagoContaAtiva::getConta)
                .orElseThrow(this::notConfigured);
        if (!conta.isActive() || conta.getAccessToken() == null || conta.getAccessToken().isBlank()
                || conta.getDataExpiracao() == null || !conta.getDataExpiracao().isAfter(Instant.now())) {
            throw notConfigured();
        }
        return conta;
    }

    @Transactional(readOnly = true)
    public MercadoPagoConta getAtivaPorMpUserId(String mpUserId) {
        MercadoPagoConta conta = ativaRepository.findWithBindingByMpUserId(mpUserId)
                .map(MercadoPagoContaAtiva::getConta)
                .orElseThrow(this::notConfigured);
        if (!conta.isActive() || conta.getAccessToken() == null || conta.getAccessToken().isBlank()) {
            throw notConfigured();
        }
        return conta;
    }

    @Transactional(readOnly = true)
    public Optional<MercadoPagoConta> findAtivaPorEmpresa(String empresaId) {
        return ativaRepository.findByEmpresaId(empresaId).map(MercadoPagoContaAtiva::getConta);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarConflito(String empresaId) {
        empresaRepository.findById(empresaId).ifPresent(empresa ->
                auditLogService.record(empresa, "MERCADO_PAGO_ACCOUNT_LINK_CONFLICT",
                        "MercadoPagoConta", null, null, null, Map.of()));
    }

    public void validarSemPagamentosAtivos(String empresaId) {
        if (pagamentoRepository.existsByEmpresaIdAndStatusIn(empresaId, ACTIVE_PAYMENT_STATUSES)) {
            throw new ApiBusinessException(HttpStatus.CONFLICT, "MERCADO_PAGO_ACTIVE_PAYMENTS",
                    "Existem pagamentos Mercado Pago ainda não resolvidos");
        }
    }

    private void desvincularInterno(Empresa empresa, String reason) {
        Optional<MercadoPagoContaAtiva> ativa = ativaRepository.findByEmpresaIdForUpdate(empresa.getId());
        if (ativa.isEmpty()) {
            pointBindingService.desvincularDaEmpresa(empresa.getId(), reason);
            return;
        }
        MercadoPagoConta conta = ativa.get().getConta();
        pointBindingService.desvincularDaConta(conta, reason);
        conta.unlink(reason);
        contaRepository.save(conta);
        ativaRepository.delete(ativa.get());
        ativaRepository.flush();
        auditLogService.record(empresa, "MERCADO_PAGO_UNLINKED", "MercadoPagoConta",
                conta.getIdMercadoConta(), Map.of("mpUserId", conta.getMpUserId()), null,
                Map.of("reason", reason));
    }

    private void validarToken(MercadoPagoTokenResponse token) {
        if (token == null || token.user_id() == null || token.user_id().isBlank()) {
            throw new ApiBusinessException(HttpStatus.BAD_GATEWAY, "MERCADO_PAGO_INVALID_OAUTH_RESPONSE",
                    "Mercado Pago não retornou a identidade da conta autorizada");
        }
    }

    private void validarEmpresaOperacional(Empresa empresa) {
        if (!Boolean.TRUE.equals(empresa.getAtivo()) || empresa.isDefinitivamenteEncerrada()) {
            throw new ApiBusinessException(HttpStatus.CONFLICT, "COMPANY_NOT_ACTIVE",
                    "A Empresa não está ativa");
        }
    }

    private ApiBusinessException alreadyLinked() {
        return new ApiBusinessException(HttpStatus.CONFLICT,
                "MERCADO_PAGO_ACCOUNT_ALREADY_LINKED",
                "Esta conta Mercado Pago já está vinculada a outra empresa no sistema.");
    }

    private ApiBusinessException replacementRequired() {
        return new ApiBusinessException(HttpStatus.CONFLICT,
                "MERCADO_PAGO_ACCOUNT_REPLACEMENT_REQUIRED",
                "Esta Empresa já possui outra conta Mercado Pago; confirme a substituição.");
    }

    private ApiBusinessException notConfigured() {
        return new ApiBusinessException(HttpStatus.CONFLICT,
                "MERCADO_PAGO_NOT_CONFIGURED",
                "Mercado Pago não está configurado para esta Empresa.");
    }

    public record AuthorizationResult(MercadoPagoConta conta, AuthorizationType type) {}
    public enum AuthorizationType { LINKED, REAUTHORIZED, REPLACED }
}
