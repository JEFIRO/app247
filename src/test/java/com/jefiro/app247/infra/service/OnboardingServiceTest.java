package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Condominio;
import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.dto.*;
import com.jefiro.app247.domain.model.terminal.Terminal;
import com.jefiro.app247.infra.dto.onboarding.CadastroCompletoRequest;
import com.jefiro.app247.infra.dto.onboarding.OnboardingResponse;
import com.jefiro.app247.infra.repository.CondominioRepository;
import com.jefiro.app247.infra.repository.TerminalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OnboardingServiceTest {
    @Mock EmpresaService empresaService;
    @Mock UserService userService;
    @Mock CondominioService condominioService;
    @Mock TerminalService terminalService;
    @Mock CondominioRepository condominioRepository;
    @Mock TerminalRepository terminalRepository;

    @Test
    void criaGestorEmpresaCondominioETerminalNaOrdemEsperada() {
        CadastroCompletoRequest request = request();
        Empresa empresa = Empresa.builder().id("empresa-a").build();
        User gestor = User.builder().idUser("gestor-a").build();
        Condominio condominio = new Condominio();
        condominio.setIdCondominio("condominio-a");
        Terminal terminal = new Terminal();
        terminal.setIdTerminal("terminal-a");

        when(empresaService.newEmpresa(any())).thenReturn(empresa);
        when(userService.cadastrarGestor(request.gestor(), empresa)).thenReturn(gestor);
        when(condominioService.construir(request.condominio(), empresa)).thenReturn(condominio);
        when(condominioRepository.save(condominio)).thenReturn(condominio);
        when(terminalService.construir(request.terminal(), condominio)).thenReturn(terminal);
        when(terminalRepository.save(terminal)).thenReturn(terminal);

        OnboardingResponse response = service().executar(request);

        assertEquals("empresa-a", response.empresaId());
        assertEquals(condominio, gestor.getCondominio());
        InOrder ordem = inOrder(empresaService, userService, condominioRepository, terminalRepository);
        ordem.verify(empresaService).newEmpresa(any());
        ordem.verify(userService).cadastrarGestor(request.gestor(), empresa);
        ordem.verify(condominioRepository).save(condominio);
        ordem.verify(terminalRepository).save(terminal);
    }

    @Test
    void falhaDoTerminalPropagaParaRollbackDaTransacao() throws Exception {
        CadastroCompletoRequest request = request();
        Empresa empresa = Empresa.builder().id("empresa-a").build();
        User gestor = User.builder().idUser("gestor-a").build();
        Condominio condominio = new Condominio();
        Terminal terminal = new Terminal();
        when(empresaService.newEmpresa(any())).thenReturn(empresa);
        when(userService.cadastrarGestor(any(), eq(empresa))).thenReturn(gestor);
        when(condominioService.construir(any(), eq(empresa))).thenReturn(condominio);
        when(condominioRepository.save(condominio)).thenReturn(condominio);
        when(terminalService.construir(any(), eq(condominio))).thenReturn(terminal);
        when(terminalRepository.save(terminal)).thenThrow(new IllegalStateException("terminal inválido"));

        assertThrows(IllegalStateException.class, () -> service().executar(request));
        assertNotNull(OnboardingService.class.getMethod("executar", CadastroCompletoRequest.class)
                .getAnnotation(Transactional.class));
    }

    private OnboardingService service() {
        return new OnboardingService(empresaService, userService, condominioService, terminalService,
                condominioRepository, terminalRepository);
    }

    private CadastroCompletoRequest request() {
        UserRequestDTO gestor = new UserRequestDTO("Ana", "Gestora", "ana@empresa.com", "123456",
                "12345678901", "71999999999", LocalDate.of(1990, 1, 1), null);
        EmpresaRequest empresa = new EmpresaRequest("Empresa A", "Empresa A", "12345678000199",
                "contato@empresa.com", "71999999999", "40000000", "Rua A", "1", "Centro", "Salvador", "BA");
        EnderecoDTO endereco = new EnderecoDTO("Rua B", "2", null, "Centro", "Salvador", "BA", "40000001");
        return new CadastroCompletoRequest(gestor, empresa,
                new CondominioRequest("Alpha", "98765432000199", endereco),
                new TerminalRequest("Recepção", "SERIAL-1", "AA:BB", "10.0.0.1"));
    }
}
