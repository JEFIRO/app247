package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Condominio;
import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.dto.CondominioRequest;
import com.jefiro.app247.domain.model.dto.TerminalRequest;
import com.jefiro.app247.domain.model.terminal.Terminal;
import com.jefiro.app247.infra.repository.CondominioRepository;
import com.jefiro.app247.infra.repository.TerminalRepository;
import jakarta.persistence.JoinColumn;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HierarquiaTenantServiceTest {
    @Mock CondominioRepository condominioRepository;
    @Mock TerminalRepository terminalRepository;
    @Mock EmpresaService empresaService;

    @AfterEach
    void limparTenant() {
        EmpresaContext.clear();
    }

    @Test
    void permiteSegundoCondominioNaMesmaEmpresa() {
        Empresa empresa = Empresa.builder().id("empresa-a").build();
        EmpresaContext.set("empresa-a");
        when(empresaService.getEmpresa("empresa-a")).thenReturn(empresa);
        when(condominioRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        CondominioService service = new CondominioService(condominioRepository, empresaService);

        service.criar(new CondominioRequest("Alpha", null, null));
        service.criar(new CondominioRequest("Beta", null, null));

        verify(condominioRepository, times(2)).save(any(Condominio.class));
    }

    @Test
    void permiteVariosTerminaisNoMesmoCondominioENoutroDaEmpresa() {
        EmpresaContext.set("empresa-a");
        Condominio alpha = condominio("alpha", "empresa-a");
        Condominio beta = condominio("beta", "empresa-a");
        CondominioService condominios = mock(CondominioService.class);
        when(condominios.buscarDoTenant("alpha", "empresa-a")).thenReturn(alpha);
        when(condominios.buscarDoTenant("beta", "empresa-a")).thenReturn(beta);
        when(terminalRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        TerminalService service = new TerminalService(terminalRepository, condominios);
        TerminalRequest request = new TerminalRequest("Terminal", "serial", null, null);

        service.criar("alpha", request);
        service.criar("alpha", request);
        service.criar("beta", request);

        verify(terminalRepository, times(3)).save(any(Terminal.class));
    }

    @Test
    void bloqueiaCondominioETerminalDeOutraEmpresaPelaConsultaComposta() {
        EmpresaContext.set("empresa-a");
        CondominioService condominios = new CondominioService(condominioRepository, empresaService);
        when(condominioRepository.findByIdCondominioAndEmpresaId("condominio-b", "empresa-a"))
                .thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> condominios.buscar("condominio-b"));

        TerminalService terminais = new TerminalService(terminalRepository, condominios);
        when(terminalRepository.findByIdTerminalAndCondominioEmpresaId("terminal-b", "empresa-a"))
                .thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> terminais.buscar("terminal-b"));
        verify(terminalRepository).findByIdTerminalAndCondominioEmpresaId("terminal-b", "empresa-a");
    }

    @Test
    void mapeamentoJpaTemSomenteCondominioComoPaiDoTerminal() throws Exception {
        assertThrows(NoSuchFieldException.class, () -> Terminal.class.getDeclaredField("empresa"));
        JoinColumn join = Terminal.class.getDeclaredField("condominio").getAnnotation(JoinColumn.class);
        assertEquals("condominio_id", join.name());
        assertFalse(join.nullable());
    }

    private Condominio condominio(String id, String empresaId) {
        Condominio condominio = new Condominio();
        condominio.setIdCondominio(id);
        condominio.setEmpresa(Empresa.builder().id(empresaId).build());
        return condominio;
    }
}
