package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.MercadoPagoConta;
import com.jefiro.app247.domain.model.TerminalPointBinding;
import com.jefiro.app247.domain.model.enum_type.TerminalLifecycleState;
import com.jefiro.app247.domain.model.terminal.Terminal;
import com.jefiro.app247.infra.exception.ApiBusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class MercadoPagoOperationalConfigurationService {
    @Autowired
    MercadoPagoAccountLifecycleService accountLifecycleService;
    @Autowired
    TerminalPointBindingService pointBindingService;

    @Transactional(readOnly = true)
    public MercadoPagoConta requireConfigured(Terminal terminal, String empresaId) {
        if (terminal == null || terminal.getCondominio() == null
                || terminal.getCondominio().getEmpresa() == null
                || !empresaId.equals(terminal.getCondominio().getEmpresa().getId())
                || terminal.getLifecycleState() != TerminalLifecycleState.ACTIVE
                || !Boolean.TRUE.equals(terminal.getAtivo())) {
            throw notConfigured();
        }
        MercadoPagoConta conta = accountLifecycleService.getAtivaPorEmpresa(empresaId);
        TerminalPointBinding point = pointBindingService.ativoDoTerminal(terminal.getIdTerminal())
                .orElseThrow(this::notConfigured);
        if (!point.getMercadoPagoConta().getIdMercadoConta().equals(conta.getIdMercadoConta())
                || terminal.getMercadoPagoTerminalId() == null
                || !terminal.getMercadoPagoTerminalId().equals(point.getMercadoPagoTerminalId())) {
            throw notConfigured();
        }
        return conta;
    }

    @Transactional(readOnly = true)
    public boolean isConfigured(Terminal terminal, String empresaId) {
        if (!terminalOperacional(terminal, empresaId)) {
            return false;
        }

        Optional<MercadoPagoConta> contaOpt = accountLifecycleService.findAtivaPorEmpresa(empresaId);
        if (contaOpt.isEmpty() || !contaOperacional(contaOpt.get())) {
            return false;
        }

        MercadoPagoConta conta = contaOpt.get();
        Optional<TerminalPointBinding> pointOpt =
                pointBindingService.ativoDoTerminal(terminal.getIdTerminal());
        if (pointOpt.isEmpty()) {
            return false;
        }

        TerminalPointBinding point = pointOpt.get();
        return point.getMercadoPagoConta() != null
                && point.getMercadoPagoConta().getIdMercadoConta()
                .equals(conta.getIdMercadoConta())
                && terminal.getMercadoPagoTerminalId() != null
                && terminal.getMercadoPagoTerminalId()
                .equals(point.getMercadoPagoTerminalId());
    }

    private boolean terminalOperacional(Terminal terminal, String empresaId) {
        return empresaId != null
                && terminal != null
                && terminal.getCondominio() != null
                && terminal.getCondominio().getEmpresa() != null
                && empresaId.equals(terminal.getCondominio().getEmpresa().getId())
                && terminal.getLifecycleState() == TerminalLifecycleState.ACTIVE
                && Boolean.TRUE.equals(terminal.getAtivo());
    }

    private boolean contaOperacional(MercadoPagoConta conta) {
        return conta.isActive()
                && conta.getAccessToken() != null
                && !conta.getAccessToken().isBlank()
                && conta.getDataExpiracao() != null
                && conta.getDataExpiracao().isAfter(Instant.now());
    }

    private ApiBusinessException notConfigured() {
        return new ApiBusinessException(HttpStatus.CONFLICT, "MERCADO_PAGO_NOT_CONFIGURED",
                "Mercado Pago não está configurado para este Terminal.");
    }
}
