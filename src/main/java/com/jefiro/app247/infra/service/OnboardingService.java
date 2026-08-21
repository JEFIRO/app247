package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Condominio;
import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.terminal.Terminal;
import com.jefiro.app247.infra.dto.onboarding.CadastroCompletoRequest;
import com.jefiro.app247.infra.dto.onboarding.OnboardingResponse;
import com.jefiro.app247.infra.repository.CondominioRepository;
import com.jefiro.app247.infra.repository.TerminalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OnboardingService {
    private final EmpresaService empresaService;
    private final UserService userService;
    private final CondominioService condominioService;
    private final TerminalService terminalService;
    private final CondominioRepository condominioRepository;
    private final TerminalRepository terminalRepository;

    public OnboardingService(EmpresaService empresaService, UserService userService,
                             CondominioService condominioService, TerminalService terminalService,
                             CondominioRepository condominioRepository, TerminalRepository terminalRepository) {
        this.empresaService = empresaService;
        this.userService = userService;
        this.condominioService = condominioService;
        this.terminalService = terminalService;
        this.condominioRepository = condominioRepository;
        this.terminalRepository = terminalRepository;
    }

    @Transactional
    public OnboardingResponse executar(CadastroCompletoRequest request) {
        Empresa empresa = empresaService.newEmpresa(new Empresa(request.empresa()));
        User gestor = userService.cadastrarGestor(request.gestor(), empresa);

        Condominio condominio = condominioService.construir(request.condominio(), empresa);
        condominio = condominioRepository.save(condominio);
        gestor.setCondominio(condominio);

        Terminal terminal = terminalService.construir(request.terminal(), condominio);
        terminal = terminalRepository.save(terminal);

        return new OnboardingResponse(gestor.getIdUser(), empresa.getId(),
                condominio.getIdCondominio(), terminal.getIdTerminal());
    }
}
