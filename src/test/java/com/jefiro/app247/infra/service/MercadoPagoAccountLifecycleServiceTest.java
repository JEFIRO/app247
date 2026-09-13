package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.MercadoPagoConta;
import com.jefiro.app247.domain.model.MercadoPagoContaAtiva;
import com.jefiro.app247.domain.model.dto.MercadoPagoTokenResponse;
import com.jefiro.app247.domain.model.enum_type.MercadoPagoAccountBindingStatus;
import com.jefiro.app247.infra.exception.ApiBusinessException;
import com.jefiro.app247.infra.repository.EmpresaRepository;
import com.jefiro.app247.infra.repository.MercadoPagoContaAtivaRepository;
import com.jefiro.app247.infra.repository.OauthMercadoPagoRepository;
import com.jefiro.app247.infra.repository.PagamentoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MercadoPagoAccountLifecycleServiceTest {
    @Mock EmpresaRepository empresaRepository;
    @Mock OauthMercadoPagoRepository contaRepository;
    @Mock MercadoPagoContaAtivaRepository ativaRepository;
    @Mock PagamentoRepository pagamentoRepository;
    @Mock TerminalPointBindingService pointBindingService;
    @Mock AuditLogService auditLogService;

    MercadoPagoAccountLifecycleService service;
    Empresa empresaA;

    @BeforeEach
    void setUp() {
        service = new MercadoPagoAccountLifecycleService();
        ReflectionTestUtils.setField(service, "empresaRepository", empresaRepository);
        ReflectionTestUtils.setField(service, "contaRepository", contaRepository);
        ReflectionTestUtils.setField(service, "ativaRepository", ativaRepository);
        ReflectionTestUtils.setField(service, "pagamentoRepository", pagamentoRepository);
        ReflectionTestUtils.setField(service, "pointBindingService", pointBindingService);
        ReflectionTestUtils.setField(service, "auditLogService", auditLogService);
        empresaA = Empresa.builder().id("empresa-a").ativo(true).build();
    }

    @Test
    void primeiraAutorizacaoCriaBindingHistoricoELeaseAtivo() {
        when(empresaRepository.findByIdForUpdate("empresa-a")).thenReturn(Optional.of(empresaA));
        when(ativaRepository.findByEmpresaIdForUpdate("empresa-a")).thenReturn(Optional.empty());
        when(ativaRepository.findWithBindingByMpUserId("mp-x")).thenReturn(Optional.empty());
        when(contaRepository.saveAndFlush(any())).thenAnswer(call -> {
            MercadoPagoConta conta = call.getArgument(0);
            conta.setIdMercadoConta("binding-a");
            return conta;
        });

        var result = service.autorizar("empresa-a", token("mp-x", "access-a"), false);

        assertThat(result.type()).isEqualTo(
                MercadoPagoAccountLifecycleService.AuthorizationType.LINKED);
        assertThat(result.conta().getEmpresa()).isSameAs(empresaA);
        assertThat(result.conta().getStatus()).isEqualTo(MercadoPagoAccountBindingStatus.ACTIVE);
        verify(ativaRepository).saveAndFlush(argThat(lease ->
                lease.getMpUserId().equals("mp-x") && lease.getEmpresa() == empresaA));
    }

    @Test
    void mesmaContaNaMesmaEmpresaEhReautorizadaSemDuplicar() {
        MercadoPagoConta conta = contaAtiva(empresaA, "binding-a", "mp-x", "access-old");
        when(empresaRepository.findByIdForUpdate("empresa-a")).thenReturn(Optional.of(empresaA));
        when(ativaRepository.findByEmpresaIdForUpdate("empresa-a"))
                .thenReturn(Optional.of(new MercadoPagoContaAtiva(conta)));

        var result = service.autorizar("empresa-a", token("mp-x", "access-new"), false);

        assertThat(result.type()).isEqualTo(
                MercadoPagoAccountLifecycleService.AuthorizationType.REAUTHORIZED);
        assertThat(conta.getAccessToken()).isEqualTo("access-new");
        verify(contaRepository).save(conta);
        verify(contaRepository, never()).saveAndFlush(any());
        verify(ativaRepository, never()).saveAndFlush(any());
    }

    @Test
    void contaAtivaEmOutraEmpresaEhRecusadaSemAlterarVinculoExistente() {
        Empresa empresaB = Empresa.builder().id("empresa-b").ativo(true).build();
        MercadoPagoConta contaB = contaAtiva(empresaB, "binding-b", "mp-x", "access-b");
        when(empresaRepository.findByIdForUpdate("empresa-a")).thenReturn(Optional.of(empresaA));
        when(ativaRepository.findByEmpresaIdForUpdate("empresa-a")).thenReturn(Optional.empty());
        when(ativaRepository.findWithBindingByMpUserId("mp-x"))
                .thenReturn(Optional.of(new MercadoPagoContaAtiva(contaB)));

        assertCode(() -> service.autorizar("empresa-a", token("mp-x", "access-a"), false),
                "MERCADO_PAGO_ACCOUNT_ALREADY_LINKED");

        verifyNoInteractions(contaRepository, pointBindingService);
        assertThat(contaB.getAccessToken()).isEqualTo("access-b");
    }

    @Test
    void contaDiferenteExigeSubstituicaoExplicita() {
        MercadoPagoConta conta = contaAtiva(empresaA, "binding-a", "mp-x", "access-a");
        when(empresaRepository.findByIdForUpdate("empresa-a")).thenReturn(Optional.of(empresaA));
        when(ativaRepository.findByEmpresaIdForUpdate("empresa-a"))
                .thenReturn(Optional.of(new MercadoPagoContaAtiva(conta)));

        assertCode(() -> service.autorizar("empresa-a", token("mp-y", "access-y"), false),
                "MERCADO_PAGO_ACCOUNT_REPLACEMENT_REQUIRED");

        verifyNoInteractions(pointBindingService);
        assertThat(conta.getStatus()).isEqualTo(MercadoPagoAccountBindingStatus.ACTIVE);
    }

    @Test
    void substituicaoConfirmadaDesativaContaEPointsAntigasAntesDaNova() {
        MercadoPagoConta antiga = contaAtiva(empresaA, "binding-a", "mp-x", "access-a");
        MercadoPagoContaAtiva lease = new MercadoPagoContaAtiva(antiga);
        when(empresaRepository.findByIdForUpdate("empresa-a")).thenReturn(Optional.of(empresaA));
        when(ativaRepository.findByEmpresaIdForUpdate("empresa-a")).thenReturn(Optional.of(lease));
        when(ativaRepository.findWithBindingByMpUserId("mp-y")).thenReturn(Optional.empty());
        when(contaRepository.saveAndFlush(any())).thenAnswer(call -> {
            MercadoPagoConta conta = call.getArgument(0);
            conta.setIdMercadoConta("binding-new");
            return conta;
        });

        var result = service.autorizar("empresa-a", token("mp-y", "access-y"), true);

        assertThat(result.type()).isEqualTo(
                MercadoPagoAccountLifecycleService.AuthorizationType.REPLACED);
        assertThat(antiga.getStatus()).isEqualTo(MercadoPagoAccountBindingStatus.UNLINKED);
        assertThat(antiga.getAccessToken()).isNull();
        verify(pointBindingService).desvincularDaConta(antiga, "MERCADO_PAGO_ACCOUNT_REPLACED");
        verify(ativaRepository).delete(lease);
        verify(ativaRepository).flush();
        verify(ativaRepository).saveAndFlush(argThat(active -> active.getMpUserId().equals("mp-y")));
    }

    @Test
    void unlinkInvalidaPointsCredenciaisELeaseMasPreservaHistorico() {
        MercadoPagoConta conta = contaAtiva(empresaA, "binding-a", "mp-x", "access-a");
        MercadoPagoContaAtiva lease = new MercadoPagoContaAtiva(conta);
        when(empresaRepository.findByIdForUpdate("empresa-a")).thenReturn(Optional.of(empresaA));
        when(ativaRepository.findByEmpresaIdForUpdate("empresa-a")).thenReturn(Optional.of(lease));

        service.desvincular("empresa-a");

        assertThat(conta.getStatus()).isEqualTo(MercadoPagoAccountBindingStatus.UNLINKED);
        assertThat(conta.getAccessToken()).isNull();
        assertThat(conta.getRefreshToken()).isNull();
        assertThat(conta.getUnlinkedAt()).isNotNull();
        verify(pointBindingService).desvincularDaConta(conta, "USER_UNLINK");
        verify(contaRepository).save(conta);
        verify(contaRepository, never()).delete(any());
        verify(ativaRepository).delete(lease);
    }

    @Test
    void pagamentoPendenteBloqueiaUnlinkOuSubstituicao() {
        when(empresaRepository.findByIdForUpdate("empresa-a")).thenReturn(Optional.of(empresaA));
        when(pagamentoRepository.existsByEmpresaIdAndStatusIn(eq("empresa-a"), any()))
                .thenReturn(true);

        assertCode(() -> service.desvincular("empresa-a"), "MERCADO_PAGO_ACTIVE_PAYMENTS");
        verifyNoInteractions(pointBindingService, ativaRepository);
    }

    private MercadoPagoTokenResponse token(String mpUserId, String accessToken) {
        return new MercadoPagoTokenResponse(accessToken, "bearer", 21600L, "offline_access",
                mpUserId, "refresh-" + accessToken, "public", true);
    }

    private MercadoPagoConta contaAtiva(Empresa empresa, String id, String mpUserId, String token) {
        Instant now = Instant.now();
        return MercadoPagoConta.builder().idMercadoConta(id).empresa(empresa).mpUserId(mpUserId)
                .accessToken(token).refreshToken("refresh").dataCriacao(now)
                .dataExpiracao(now.plusSeconds(21600)).status(MercadoPagoAccountBindingStatus.ACTIVE)
                .linkedAt(now).build();
    }

    private void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation,
                            String expected) {
        assertThatThrownBy(operation).isInstanceOf(ApiBusinessException.class)
                .extracting(error -> ((ApiBusinessException) error).getCode())
                .isEqualTo(expected);
    }
}
