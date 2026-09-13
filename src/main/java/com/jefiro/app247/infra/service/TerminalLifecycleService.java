package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.dto.TerminalBootstrapResponse;
import com.jefiro.app247.domain.model.enum_type.TerminalBootstrapState;
import com.jefiro.app247.domain.model.enum_type.TerminalLifecycleState;
import com.jefiro.app247.domain.model.enum_type.TerminalStatus;
import com.jefiro.app247.domain.model.terminal.Terminal;
import com.jefiro.app247.infra.event.TerminalFactoryResetRequiredEvent;
import com.jefiro.app247.infra.repository.TerminalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
public class TerminalLifecycleService {
    @Autowired
    TerminalRepository terminalRepository;
    @Autowired
    MercadoPagoOperationalConfigurationService configurationService;
    @Autowired
    TerminalPointBindingService pointBindingService;
    @Autowired
    AuditLogService auditLogService;
    @Autowired
    ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public TerminalBootstrapResponse consultar(String terminalId) {
        Optional<Terminal> terminalOpt = terminalRepository.findById(terminalId);
        if (terminalOpt.isEmpty()) {
            return new TerminalBootstrapResponse(terminalId, TerminalBootstrapState.RESET_REQUIRED,
                    false, "TERMINAL_NOT_FOUND", null);
        }
        Terminal terminal = terminalOpt.get();
        Empresa empresa = terminal.getCondominio().getEmpresa();
        if (terminal.getLifecycleState() == TerminalLifecycleState.RESET_REQUIRED
                || empresa.isDefinitivamenteEncerrada()) {
            return response(terminal, TerminalBootstrapState.RESET_REQUIRED, false,
                    terminal.getResetReason() == null ? "COMPANY_CLOSED" : terminal.getResetReason());
        }
        if (terminal.getLifecycleState() == TerminalLifecycleState.UNACTIVATED) {
            return response(terminal, TerminalBootstrapState.UNACTIVATED, false, null);
        }
        if (!Boolean.TRUE.equals(empresa.getAtivo()) || !Boolean.TRUE.equals(terminal.getAtivo())) {
            return response(terminal, TerminalBootstrapState.DISABLED, false, null);
        }
        boolean paymentConfigured = configurationService.isConfigured(terminal, empresa.getId());
        return response(terminal,
                paymentConfigured ? TerminalBootstrapState.ACTIVE
                        : TerminalBootstrapState.PAYMENT_NOT_CONFIGURED,
                paymentConfigured, null);
    }

    @Transactional
    public int marcarResetRequired(Empresa empresa, String reason) {
        pointBindingService.desvincularDaEmpresa(empresa.getId(), reason);
        int changed = 0;
        for (Terminal terminal : terminalRepository.findAllByCondominioEmpresaIdOrderByNome(empresa.getId())) {
            if (terminal.getLifecycleState() == TerminalLifecycleState.UNACTIVATED) continue;
            if (terminal.getLifecycleState() != TerminalLifecycleState.RESET_REQUIRED) {
                terminal.setLifecycleState(TerminalLifecycleState.RESET_REQUIRED);
                terminal.setResetRequestedAt(Instant.now());
                terminal.setResetStartedAt(null);
                terminal.setResetCompletedAt(null);
                terminal.setResetReason(reason);
                terminal.setAtivo(false);
                terminal.setMercadoPagoTerminalId(null);
                terminalRepository.save(terminal);
                auditLogService.record(empresa, "TERMINAL_RESET_REQUIRED", "Terminal",
                        terminal.getIdTerminal(), null, Map.of("state", "RESET_REQUIRED"),
                        Map.of("reason", reason));
                eventPublisher.publishEvent(new TerminalFactoryResetRequiredEvent(
                        terminal.getIdTerminal(), reason, terminal.getResetRequestedAt()));
                changed++;
            }
        }
        return changed;
    }

    @Transactional
    public void confirmarInicio(String terminalId) {
        terminalRepository.findById(terminalId).ifPresent(terminal -> {
            if (terminal.getLifecycleState() != TerminalLifecycleState.RESET_REQUIRED
                    || terminal.getResetStartedAt() != null) return;
            terminal.setResetStartedAt(Instant.now());
            terminalRepository.save(terminal);
            auditLogService.record(terminal.getCondominio().getEmpresa(),
                    "TERMINAL_FACTORY_RESET_STARTED", "Terminal", terminalId,
                    null, Map.of("state", "RESET_STARTED"), Map.of());
        });
    }

    @Transactional
    public void confirmarConclusao(String terminalId) {
        terminalRepository.findById(terminalId).ifPresent(terminal -> {
            if (terminal.getLifecycleState() == TerminalLifecycleState.UNACTIVATED) return;
            if (terminal.getLifecycleState() != TerminalLifecycleState.RESET_REQUIRED) return;
            terminal.setLifecycleState(TerminalLifecycleState.UNACTIVATED);
            terminal.setResetCompletedAt(Instant.now());
            terminal.setSerialNumber(null);
            terminal.setMacAddress(null);
            terminal.setIpAddress(null);
            terminal.setMercadoPagoTerminalId(null);
            terminal.setLastPing(null);
            terminal.setStatus(TerminalStatus.OFFLINE);
            terminalRepository.save(terminal);
            auditLogService.record(terminal.getCondominio().getEmpresa(),
                    "TERMINAL_FACTORY_RESET_COMPLETED", "Terminal", terminalId,
                    Map.of("state", "RESET_REQUIRED"), Map.of("state", "UNACTIVATED"),
                    Map.of());
        });
    }

    private TerminalBootstrapResponse response(Terminal terminal, TerminalBootstrapState state,
                                               boolean paymentConfigured, String reason) {
        return new TerminalBootstrapResponse(terminal.getIdTerminal(), state, paymentConfigured,
                reason, terminal.getResetRequestedAt());
    }
}
