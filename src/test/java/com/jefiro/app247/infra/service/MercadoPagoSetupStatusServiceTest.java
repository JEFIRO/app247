package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.MercadoPagoConta;
import com.jefiro.app247.domain.model.terminal.Terminal;
import com.jefiro.app247.infra.dto.mercadopago.MercadoPagoSetupStatusResponse;
import com.jefiro.app247.infra.repository.OauthMercadoPagoRepository;
import com.jefiro.app247.infra.repository.TerminalRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MercadoPagoSetupStatusServiceTest {
    @Mock OauthMercadoPagoRepository contaRepository;
    @Mock TerminalRepository terminalRepository;

    private MercadoPagoSetupStatusService service;

    @BeforeEach
    void setUp() {
        EmpresaContext.set("empresa-a");
        service = new MercadoPagoSetupStatusService(contaRepository, terminalRepository);
    }

    @AfterEach
    void tearDown() {
        EmpresaContext.clear();
    }

    @Test
    void informaConfiguracaoAusente() {
        when(contaRepository.findByEmpresaId("empresa-a")).thenReturn(Optional.empty());
        when(terminalRepository.countByCondominioEmpresaId("empresa-a")).thenReturn(3L);
        when(terminalRepository.findAllByCondominioEmpresaIdAndMercadoPagoTerminalIdIsNotNull("empresa-a"))
                .thenReturn(List.of());

        MercadoPagoSetupStatusResponse status = service.consultar();

        assertThat(status.contaVinculada()).isFalse();
        assertThat(status.maquininhaVinculada()).isFalse();
        assertThat(status.configuracaoCompleta()).isFalse();
        assertThat(status.quantidadeTerminais()).isEqualTo(3);
    }

    @Test
    void contaValidaSemMaquininhaContinuaPendente() {
        when(contaRepository.findByEmpresaId("empresa-a")).thenReturn(Optional.of(contaValida()));
        when(terminalRepository.countByCondominioEmpresaId("empresa-a")).thenReturn(1L);
        when(terminalRepository.findAllByCondominioEmpresaIdAndMercadoPagoTerminalIdIsNotNull("empresa-a"))
                .thenReturn(List.of());

        MercadoPagoSetupStatusResponse status = service.consultar();

        assertThat(status.contaVinculada()).isTrue();
        assertThat(status.maquininhaVinculada()).isFalse();
        assertThat(status.configuracaoCompleta()).isFalse();
    }

    @Test
    void contaValidaEQualquerMaquininhaConcluemConfiguracaoMinima() {
        Terminal terminal = new Terminal();
        terminal.setMercadoPagoTerminalId("POINT-1");
        when(contaRepository.findByEmpresaId("empresa-a")).thenReturn(Optional.of(contaValida()));
        when(terminalRepository.countByCondominioEmpresaId("empresa-a")).thenReturn(3L);
        when(terminalRepository.findAllByCondominioEmpresaIdAndMercadoPagoTerminalIdIsNotNull("empresa-a"))
                .thenReturn(List.of(terminal));

        MercadoPagoSetupStatusResponse status = service.consultar();

        assertThat(status.configuracaoCompleta()).isTrue();
        assertThat(status.quantidadeMaquininhasVinculadas()).isEqualTo(1);
    }

    @Test
    void autorizacaoExpiradaNaoContaComoVinculada() {
        MercadoPagoConta conta = contaValida();
        conta.setDataExpiracao(LocalDateTime.now().minusMinutes(1));
        when(contaRepository.findByEmpresaId("empresa-a")).thenReturn(Optional.of(conta));
        when(terminalRepository.countByCondominioEmpresaId("empresa-a")).thenReturn(0L);
        when(terminalRepository.findAllByCondominioEmpresaIdAndMercadoPagoTerminalIdIsNotNull("empresa-a"))
                .thenReturn(List.of());

        assertThat(service.consultar().contaVinculada()).isFalse();
    }

    private MercadoPagoConta contaValida() {
        return MercadoPagoConta.builder()
                .accessToken("token")
                .dataExpiracao(LocalDateTime.now().plusHours(1))
                .build();
    }
}
