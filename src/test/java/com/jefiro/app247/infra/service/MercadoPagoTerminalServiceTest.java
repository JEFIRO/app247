package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.*;
import com.jefiro.app247.domain.model.dto.mercadopago.*;
import com.jefiro.app247.domain.model.terminal.Terminal;
import com.jefiro.app247.infra.repository.TerminalRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MercadoPagoTerminalServiceTest {
    @Mock OauthMercadoPagoService oauthService;
    @Mock TerminalService terminalService;
    @Mock TerminalRepository terminalRepository;
    @Mock RestTemplate restTemplate;

    @AfterEach
    void limparContexto() {
        EmpresaContext.clear();
    }

    @Test
    void vinculaMaquininhaDaContaAoTerminalDoTenant() {
        EmpresaContext.set("empresa-a");
        MercadoPagoConta conta = MercadoPagoConta.builder().accessToken("token-a").build();
        Terminal terminal = terminal("interno-a", "empresa-a");
        when(oauthService.getByEmpresa("empresa-a")).thenReturn(conta);
        when(terminalService.getTerminalDoTenant("interno-a")).thenReturn(terminal);
        mockTerminaisExternos("MP-A");
        when(terminalRepository.findByMercadoPagoTerminalId("MP-A")).thenReturn(Optional.empty());
        when(terminalRepository.save(terminal)).thenReturn(terminal);

        var response = service().vincular("interno-a", "MP-A");

        assertEquals("MP-A", terminal.getMercadoPagoTerminalId());
        assertEquals("MP-A", response.mercadoPagoTerminalId());
    }

    @Test
    void impedeMaquininhaAusenteNaContaEDuplicadaEmOutroTerminal() {
        EmpresaContext.set("empresa-a");
        MercadoPagoConta conta = MercadoPagoConta.builder().accessToken("token-a").build();
        Terminal terminal = terminal("interno-a", "empresa-a");
        when(oauthService.getByEmpresa("empresa-a")).thenReturn(conta);
        when(terminalService.getTerminalDoTenant("interno-a")).thenReturn(terminal);
        mockTerminaisExternos("MP-B");

        assertThrows(IllegalArgumentException.class, () -> service().vincular("interno-a", "MP-A"));

        mockTerminaisExternos("MP-A");
        when(terminalRepository.findByMercadoPagoTerminalId("MP-A"))
                .thenReturn(Optional.of(terminal("interno-b", "empresa-b")));
        assertThrows(IllegalStateException.class, () -> service().vincular("interno-a", "MP-A"));
    }

    @Test
    void naoContornaValidacaoTenantDoTerminalInterno() {
        EmpresaContext.set("empresa-a");
        when(oauthService.getByEmpresa("empresa-a"))
                .thenReturn(MercadoPagoConta.builder().accessToken("token-a").build());
        when(terminalService.getTerminalDoTenant("terminal-b"))
                .thenThrow(new RuntimeException("terminal de outro tenant"));

        assertThrows(RuntimeException.class, () -> service().vincular("terminal-b", "MP-B"));
        verifyNoInteractions(restTemplate);
    }

    private MercadoPagoTerminalService service() {
        return new MercadoPagoTerminalService(oauthService, terminalService, terminalRepository, restTemplate);
    }

    private void mockTerminaisExternos(String id) {
        ListaTerminaisResponse body = new ListaTerminaisResponse(
                new Data(List.of(new TerminalResponse(id, "pos", "store", "external", "point"))), null);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(ListaTerminaisResponse.class))).thenReturn(ResponseEntity.ok(body));
    }

    private Terminal terminal(String id, String empresaId) {
        Empresa empresa = Empresa.builder().id(empresaId).build();
        Condominio condominio = new Condominio();
        condominio.setIdCondominio("condominio-" + id);
        condominio.setEmpresa(empresa);
        Terminal terminal = new Terminal();
        terminal.setIdTerminal(id);
        terminal.setCondominio(condominio);
        return terminal;
    }
}
