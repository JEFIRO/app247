package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Condominio;
import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.MercadoPagoConta;
import com.jefiro.app247.domain.model.TerminalPointBinding;
import com.jefiro.app247.domain.model.enum_type.MercadoPagoAccountBindingStatus;
import com.jefiro.app247.domain.model.enum_type.TerminalLifecycleState;
import com.jefiro.app247.domain.model.terminal.Terminal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MercadoPagoOperationalConfigurationServiceTest {
    @Mock MercadoPagoAccountLifecycleService accountLifecycleService;
    @Mock TerminalPointBindingService pointBindingService;

    MercadoPagoOperationalConfigurationService service;

    @BeforeEach
    void setUp() {
        service = new MercadoPagoOperationalConfigurationService();
        ReflectionTestUtils.setField(service, "accountLifecycleService", accountLifecycleService);
        ReflectionTestUtils.setField(service, "pointBindingService", pointBindingService);
    }

    @Test
    void consultaSemContaRetornaFalseSemUsarExcecaoComoFluxo() {
        Terminal terminal = terminalAtivo();
        when(accountLifecycleService.findAtivaPorEmpresa("empresa-a"))
                .thenReturn(Optional.empty());

        assertThat(service.isConfigured(terminal, "empresa-a")).isFalse();

        verify(accountLifecycleService, never()).getAtivaPorEmpresa("empresa-a");
        verifyNoInteractions(pointBindingService);
    }

    @Test
    void consultaComContaExpiradaRetornaFalseSemConsultarBinding() {
        Terminal terminal = terminalAtivo();
        MercadoPagoConta conta = contaAtiva(Instant.now().minusSeconds(1));
        when(accountLifecycleService.findAtivaPorEmpresa("empresa-a"))
                .thenReturn(Optional.of(conta));

        assertThat(service.isConfigured(terminal, "empresa-a")).isFalse();

        verifyNoInteractions(pointBindingService);
    }

    @Test
    void consultaComContaEBindingCorrespondentesRetornaTrue() {
        Terminal terminal = terminalAtivo();
        MercadoPagoConta conta = contaAtiva(Instant.now().plusSeconds(3600));
        TerminalPointBinding binding = new TerminalPointBinding();
        binding.setMercadoPagoConta(conta);
        binding.setMercadoPagoTerminalId("point-a");
        when(accountLifecycleService.findAtivaPorEmpresa("empresa-a"))
                .thenReturn(Optional.of(conta));
        when(pointBindingService.ativoDoTerminal("terminal-a"))
                .thenReturn(Optional.of(binding));

        assertThat(service.isConfigured(terminal, "empresa-a")).isTrue();
    }

    private Terminal terminalAtivo() {
        Empresa empresa = Empresa.builder().id("empresa-a").ativo(true).build();
        Condominio condominio = new Condominio();
        condominio.setEmpresa(empresa);
        Terminal terminal = new Terminal();
        terminal.setIdTerminal("terminal-a");
        terminal.setCondominio(condominio);
        terminal.setAtivo(true);
        terminal.setLifecycleState(TerminalLifecycleState.ACTIVE);
        terminal.setMercadoPagoTerminalId("point-a");
        return terminal;
    }

    private MercadoPagoConta contaAtiva(Instant expiracao) {
        return MercadoPagoConta.builder()
                .idMercadoConta("conta-a")
                .accessToken("token")
                .dataExpiracao(expiracao)
                .status(MercadoPagoAccountBindingStatus.ACTIVE)
                .build();
    }
}
