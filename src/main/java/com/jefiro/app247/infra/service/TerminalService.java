package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Condominio;
import com.jefiro.app247.domain.model.dto.TerminalActivationResponse;
import com.jefiro.app247.domain.model.dto.TerminalRequest;
import com.jefiro.app247.domain.model.dto.TerminalResponseDTO;
import com.jefiro.app247.domain.model.dto.TerminalStatusDTO;
import com.jefiro.app247.domain.model.enum_type.TerminalStatus;
import com.jefiro.app247.domain.model.terminal.Terminal;
import com.jefiro.app247.infra.exception.TerminalNotFoundException;
import com.jefiro.app247.infra.repository.TerminalRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TerminalService {
    private final TerminalRepository repository;
    private final CondominioService condominioService;

    public TerminalService(TerminalRepository repository, CondominioService condominioService) {
        this.repository = repository;
        this.condominioService = condominioService;
    }

    public Terminal save(Terminal terminal) {
        return repository.save(terminal);
    }

    @Transactional
    public TerminalResponseDTO criar(String condominioId, TerminalRequest request) {
        Condominio condominio = condominioService.buscarDoTenant(condominioId, EmpresaContext.require());
        Terminal terminal = construir(request, condominio);
        return new TerminalResponseDTO(repository.save(terminal));
    }

    public List<TerminalResponseDTO> listar(String condominioId) {
        String empresaId = EmpresaContext.require();
        condominioService.buscarDoTenant(condominioId, empresaId);
        return repository.findAllByCondominioIdCondominioAndCondominioEmpresaIdOrderByNome(condominioId, empresaId)
                .stream().map(TerminalResponseDTO::new).toList();
    }

    public List<TerminalResponseDTO> listarTodos() {
        return repository.findAllByCondominioEmpresaIdOrderByNome(EmpresaContext.require())
                .stream().map(TerminalResponseDTO::new).toList();
    }

    public TerminalResponseDTO buscar(String terminalId) {
        return new TerminalResponseDTO(getTerminalDoTenant(terminalId));
    }

    @Transactional
    public TerminalResponseDTO atualizar(String terminalId, TerminalRequest request) {
        Terminal terminal = getTerminalDoTenant(terminalId);
        terminal.setNome(request.nome());
        terminal.setSerialNumber(request.serialNumber());
        terminal.setCodigo(request.serialNumber());
        terminal.setMacAddress(request.macAddress());
        terminal.setIpAddress(request.ipAddress());
        terminal.setUpdate_at(LocalDateTime.now());
        return new TerminalResponseDTO(repository.save(terminal));
    }

    public TerminalActivationResponse getBySerial(String serial) {
        return repository.findBySerialNumber(serial)
                .map(TerminalActivationResponse::new)
                .orElseThrow(TerminalNotFoundException::new);
    }

    public Terminal getTerminal(String id) {
        return repository.findById(id).orElseThrow(TerminalNotFoundException::new);
    }

    public Terminal getTerminalDoTenant(String id) {
        return repository.findByIdTerminalAndCondominioEmpresaId(id, EmpresaContext.require())
                .orElseThrow(TerminalNotFoundException::new);
    }

    @Transactional
    public Terminal updateStatus(TerminalStatusDTO status) {
        Terminal terminal = getTerminal(status.terminalId());
        terminal.setStatus(TerminalStatus.valueOf(status.status()));
        terminal.setUpdate_at(LocalDateTime.now());
        terminal.setLastPing(LocalDateTime.now());
        return repository.saveAndFlush(terminal);
    }

    Terminal construir(TerminalRequest request, Condominio condominio) {
        Terminal terminal = new Terminal(request);
        terminal.setCondominio(condominio);
        return terminal;
    }

    @Scheduled(fixedRate = 60000)
    public void verificarTerminais() {
        repository.findAll().forEach(terminal -> {
            if (terminal.getLastPing() != null
                    && terminal.getLastPing().isBefore(LocalDateTime.now().minusSeconds(60))) {
                terminal.setStatus(TerminalStatus.OFFLINE);
                repository.save(terminal);
            }
        });
    }
}
