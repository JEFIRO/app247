package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.MercadoPagoConta;
import com.jefiro.app247.domain.model.TerminalPointBinding;
import com.jefiro.app247.domain.model.enum_type.TerminalPointBindingStatus;
import com.jefiro.app247.domain.model.terminal.Terminal;
import com.jefiro.app247.infra.repository.TerminalPointBindingRepository;
import com.jefiro.app247.infra.repository.TerminalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class TerminalPointBindingService {
    @Autowired
    TerminalPointBindingRepository bindingRepository;
    @Autowired
    TerminalRepository terminalRepository;
    @Autowired
    AuditLogService auditLogService;

    @Transactional
    public Terminal vincular(Terminal terminal, MercadoPagoConta conta, String pointId) {
        Optional<TerminalPointBinding> atual = ativoDoTerminal(terminal.getIdTerminal());
        if (atual.isPresent()
                && atual.get().getMercadoPagoConta().getIdMercadoConta().equals(conta.getIdMercadoConta())
                && atual.get().getMercadoPagoTerminalId().equals(pointId)) {
            return terminal;
        }

        atual.ifPresent(binding -> desvincular(binding, "POINT_REPLACED"));
        TerminalPointBinding binding = bindingRepository.save(
                new TerminalPointBinding(terminal, conta, pointId));
        terminal.setMercadoPagoTerminalId(pointId);
        terminalRepository.saveAndFlush(terminal);
        auditLogService.record(conta.getEmpresa(), "POINT_LINKED", "TerminalPointBinding",
                binding.getId(), null,
                Map.of("terminalId", terminal.getIdTerminal(), "pointId", pointId), Map.of());
        return terminal;
    }

    @Transactional
    public boolean desvincularTerminal(Terminal terminal, String reason) {
        Optional<TerminalPointBinding> atual = ativoDoTerminal(terminal.getIdTerminal());
        if (atual.isPresent()) {
            desvincular(atual.get(), reason);
            return true;
        }
        if (terminal.getMercadoPagoTerminalId() != null) {
            String pointId = terminal.getMercadoPagoTerminalId();
            terminal.setMercadoPagoTerminalId(null);
            terminalRepository.save(terminal);
            auditLogService.record(terminal.getCondominio().getEmpresa(), "POINT_UNLINKED", "Terminal",
                    terminal.getIdTerminal(), Map.of("pointId", pointId), null,
                    Map.of("reason", reason));
            return true;
        }
        return false;
    }

    @Transactional
    public int desvincularDaConta(MercadoPagoConta conta, String reason) {
        List<TerminalPointBinding> bindings = bindingRepository
                .findAllByMercadoPagoContaIdMercadoContaAndStatus(
                        conta.getIdMercadoConta(), TerminalPointBindingStatus.ACTIVE);
        bindings.forEach(binding -> desvincular(binding, reason));
        return bindings.size();
    }

    @Transactional
    public int desvincularDaEmpresa(String empresaId, String reason) {
        List<TerminalPointBinding> bindings = bindingRepository
                .findAllByEmpresaIdAndStatus(empresaId, TerminalPointBindingStatus.ACTIVE);
        bindings.forEach(binding -> desvincular(binding, reason));

        List<Terminal> legados = terminalRepository
                .findAllByCondominioEmpresaIdAndMercadoPagoTerminalIdIsNotNull(empresaId);
        for (Terminal terminal : legados) {
            if (ativoDoTerminal(terminal.getIdTerminal()).isEmpty()) {
                terminal.setMercadoPagoTerminalId(null);
                terminalRepository.save(terminal);
            }
        }
        return bindings.size() + legados.size();
    }

    @Transactional(readOnly = true)
    public Optional<TerminalPointBinding> ativoDoTerminal(String terminalId) {
        return bindingRepository.findFirstByTerminalIdTerminalAndStatusOrderByLinkedAtDesc(
                terminalId, TerminalPointBindingStatus.ACTIVE);
    }

    private void desvincular(TerminalPointBinding binding, String reason) {
        Terminal terminal = binding.getTerminal();
        String pointId = binding.getMercadoPagoTerminalId();
        binding.unlink(reason);
        bindingRepository.save(binding);
        if (pointId.equals(terminal.getMercadoPagoTerminalId())) {
            terminal.setMercadoPagoTerminalId(null);
            terminalRepository.save(terminal);
        }
        auditLogService.record(binding.getEmpresa(), "POINT_UNLINKED", "TerminalPointBinding",
                binding.getId(), Map.of("terminalId", terminal.getIdTerminal(), "pointId", pointId),
                null, Map.of("reason", reason));
    }
}
