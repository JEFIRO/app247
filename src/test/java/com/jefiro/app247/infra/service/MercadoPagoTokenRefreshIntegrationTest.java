package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.AuditLog;
import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.MercadoPagoConta;
import com.jefiro.app247.domain.model.MercadoPagoContaAtiva;
import com.jefiro.app247.domain.model.dto.MercadoPagoTokenResponse;
import com.jefiro.app247.domain.model.enum_type.MercadoPagoAccountBindingStatus;
import com.jefiro.app247.infra.exception.ApiBusinessException;
import com.jefiro.app247.infra.exception.ExternalServiceException;
import com.jefiro.app247.infra.repository.AuditLogRepository;
import com.jefiro.app247.infra.repository.EmpresaRepository;
import com.jefiro.app247.infra.repository.MercadoPagoContaAtivaRepository;
import com.jefiro.app247.infra.repository.OauthMercadoPagoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.*;

/** Renovação do token OAuth do Mercado Pago contra o banco real; só a chamada HTTP é simulada. */
@SpringBootTest
@ActiveProfiles("test")
class MercadoPagoTokenRefreshIntegrationTest {
    private static final long SEIS_MESES = 15_552_000L;

    @Autowired MercadoPagoTokenRefreshService refresh;
    @Autowired MercadoPagoAccountLifecycleService lifecycle;
    @Autowired EmpresaRepository empresas;
    @Autowired OauthMercadoPagoRepository contas;
    @Autowired MercadoPagoContaAtivaRepository ativas;
    @Autowired AuditLogRepository auditLogs;
    @MockitoBean OauthMercadoPagoService oauth;

    @Test
    void tokenPertoDeVencerEhRenovadoComONovoRefreshToken() {
        MercadoPagoConta conta = contaVinculada(Duration.ofDays(10));
        when(oauth.renovarToken(conta.getRefreshToken())).thenReturn(token(conta.getMpUserId(), "access-2", "refresh-2"));

        refresh.renovarExpirando();

        MercadoPagoConta salva = contas.findById(conta.getIdMercadoConta()).orElseThrow();
        assertThat(salva.getAccessToken()).isEqualTo("access-2");
        assertThat(salva.getRefreshToken()).isEqualTo("refresh-2");
        assertThat(salva.getDataExpiracao()).isCloseTo(Instant.now().plusSeconds(SEIS_MESES), within(Duration.ofMinutes(1)));
        assertThat(salva.getLastError()).isNull();
        assertThat(actions(conta)).contains("MERCADO_PAGO_TOKEN_REFRESHED");
        assertThat(lifecycle.getAtivaPorEmpresa(conta.getEmpresa().getId()).getAccessToken()).isEqualTo("access-2");
    }

    @Test
    void tokenJaVencidoTambemTentaRenovar() {
        MercadoPagoConta conta = contaVinculada(Duration.ofDays(-1));
        when(oauth.renovarToken(conta.getRefreshToken())).thenReturn(token(conta.getMpUserId(), "access-2", "refresh-2"));

        refresh.renovarExpirando();

        assertThat(contas.findById(conta.getIdMercadoConta()).orElseThrow().getAccessToken()).isEqualTo("access-2");
    }

    @Test
    void tokenLongeDeVencerNaoEhTocado() {
        MercadoPagoConta conta = contaVinculada(Duration.ofDays(60));

        refresh.renovarExpirando();

        verify(oauth, never()).renovarToken(conta.getRefreshToken());
    }

    @Test
    void contaDesvinculadaNaoEhRenovada() {
        MercadoPagoConta conta = contaVinculada(Duration.ofDays(5));
        lifecycle.desvincularParaEncerramento(conta.getEmpresa());

        refresh.renovarExpirando();

        // Outros testes deixam contas no mesmo banco: verifica só a credencial desta.
        verify(oauth, never()).renovarToken(conta.getRefreshToken());
        assertThat(contas.findById(conta.getIdMercadoConta()).orElseThrow().getRefreshToken()).isNull();
    }

    @Test
    void recusaDoMercadoPagoPreservaCredencialERegistraOMotivo() {
        MercadoPagoConta conta = contaVinculada(Duration.ofDays(10));
        when(oauth.renovarToken(conta.getRefreshToken())).thenThrow(new ApiBusinessException(
                HttpStatus.BAD_GATEWAY, "MERCADO_PAGO_REFRESH_REJECTED", "recusado"));

        refresh.renovarExpirando();

        MercadoPagoConta salva = contas.findById(conta.getIdMercadoConta()).orElseThrow();
        assertThat(salva.getAccessToken()).isEqualTo(conta.getAccessToken());
        assertThat(salva.getRefreshToken()).isEqualTo(conta.getRefreshToken());
        assertThat(salva.getStatus()).isEqualTo(MercadoPagoAccountBindingStatus.ACTIVE);
        assertThat(salva.getLastError()).startsWith("REFRESH_REJECTED");
        assertThat(actions(conta)).contains("MERCADO_PAGO_TOKEN_REFRESH_FAILED");
    }

    @Test
    void falhaTransitoriaFicaRegistradaETentaDeNovoNoProximoCiclo() {
        MercadoPagoConta conta = contaVinculada(Duration.ofDays(10));
        when(oauth.renovarToken(conta.getRefreshToken()))
                .thenThrow(new ExternalServiceException("Mercado Pago", "timeout", new RuntimeException()))
                .thenReturn(token(conta.getMpUserId(), "access-2", "refresh-2"));

        refresh.renovarExpirando();
        assertThat(contas.findById(conta.getIdMercadoConta()).orElseThrow().getLastError()).startsWith("REFRESH_FAILED");

        refresh.renovarExpirando();
        MercadoPagoConta salva = contas.findById(conta.getIdMercadoConta()).orElseThrow();
        assertThat(salva.getAccessToken()).isEqualTo("access-2");
        assertThat(salva.getLastError()).isNull();
    }

    @Test
    void respostaDeOutraContaNaoSubstituiACredencial() {
        MercadoPagoConta conta = contaVinculada(Duration.ofDays(10));
        when(oauth.renovarToken(conta.getRefreshToken())).thenReturn(token("outra-conta", "access-x", "refresh-x"));

        refresh.renovarExpirando();

        MercadoPagoConta salva = contas.findById(conta.getIdMercadoConta()).orElseThrow();
        assertThat(salva.getAccessToken()).isEqualTo(conta.getAccessToken());
        assertThat(salva.getMpUserId()).isEqualTo(conta.getMpUserId());
        assertThat(salva.getLastError()).startsWith("REFRESH_ACCOUNT_MISMATCH");
    }

    @Test
    void webhookNaoUsaTokenVencido() {
        MercadoPagoConta conta = contaVinculada(Duration.ofMinutes(-1));

        assertThatThrownBy(() -> lifecycle.getAtivaPorMpUserId(conta.getMpUserId()))
                .extracting(e -> ((ApiBusinessException) e).getCode()).isEqualTo("MERCADO_PAGO_NOT_CONFIGURED");
    }

    private List<String> actions(MercadoPagoConta conta) {
        return auditLogs.findAllByEmpresaIdOrderByCreatedAtDesc(conta.getEmpresa().getId(), PageRequest.of(0, 20))
                .map(AuditLog::getAction).getContent();
    }

    private MercadoPagoConta contaVinculada(Duration venceEm) {
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        Empresa empresa = new Empresa();
        empresa.setRazaoSocial("Empresa MP " + sufixo);
        empresa.setNomeFantasia("Empresa MP " + sufixo);
        empresa.setCnpj(UUID.randomUUID().toString().substring(0, 18));
        empresa.setEmail("mp-" + sufixo + "@example.test");
        empresa.setAtivo(true);
        empresa = empresas.saveAndFlush(empresa);

        Instant agora = Instant.now();
        MercadoPagoConta conta = MercadoPagoConta.builder()
                .empresa(empresa).mpUserId("mp-" + sufixo)
                .accessToken("access-" + sufixo).refreshToken("refresh-" + sufixo)
                .dataCriacao(agora).dataExpiracao(agora.plus(venceEm))
                .linkedAt(agora).build();
        conta = contas.saveAndFlush(conta);
        ativas.saveAndFlush(new MercadoPagoContaAtiva(conta));
        return conta;
    }

    private static MercadoPagoTokenResponse token(String userId, String access, String refreshToken) {
        return new MercadoPagoTokenResponse(access, "bearer", SEIS_MESES, "offline_access", userId,
                refreshToken, "public", true);
    }
}
