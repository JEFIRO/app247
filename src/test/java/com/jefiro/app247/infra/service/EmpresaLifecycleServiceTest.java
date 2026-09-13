package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.auth.RoleUser;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.infra.exception.ApiBusinessException;
import com.jefiro.app247.infra.repository.EmpresaRepository;
import com.jefiro.app247.infra.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmpresaLifecycleServiceTest {
    @Mock EmpresaRepository empresaRepository;
    @Mock MercadoPagoAccountLifecycleService mercadoPagoLifecycleService;
    @Mock TerminalLifecycleService terminalLifecycleService;
    @Mock TerminalPointBindingService pointBindingService;
    @Mock UserRepository userRepository;
    @Mock AuditLogService auditLogService;

    EmpresaService service;
    Empresa empresa;
    User admin;

    @BeforeEach
    void setUp() {
        service = new EmpresaService();
        ReflectionTestUtils.setField(service, "repository", empresaRepository);
        ReflectionTestUtils.setField(service, "mercadoPagoLifecycleService", mercadoPagoLifecycleService);
        ReflectionTestUtils.setField(service, "terminalLifecycleService", terminalLifecycleService);
        ReflectionTestUtils.setField(service, "pointBindingService", pointBindingService);
        ReflectionTestUtils.setField(service, "userRepository", userRepository);
        ReflectionTestUtils.setField(service, "auditLogService", auditLogService);
        empresa = Empresa.builder().id("empresa-a").ativo(true).build();
        admin = User.builder().idUser("admin-a").empresa(empresa).role(RoleUser.ADMIN).build();
        EmpresaContext.set("empresa-a");
    }

    @AfterEach
    void clearContext() {
        EmpresaContext.clear();
    }

    @Test
    void encerramentoDefinitivoDesvinculaPagamentoMarcaResetESoftDelete() {
        when(empresaRepository.findByIdForUpdate("empresa-a")).thenReturn(Optional.of(empresa));

        service.encerrarDefinitivamente(admin);

        InOrder order = inOrder(mercadoPagoLifecycleService, pointBindingService,
                terminalLifecycleService, empresaRepository, userRepository);
        order.verify(mercadoPagoLifecycleService).validarSemPagamentosAtivos("empresa-a");
        order.verify(mercadoPagoLifecycleService).desvincularParaEncerramento(empresa);
        order.verify(pointBindingService).desvincularDaEmpresa("empresa-a", "COMPANY_CLOSED");
        order.verify(terminalLifecycleService).marcarResetRequired(empresa, "COMPANY_CLOSED");
        order.verify(empresaRepository).save(empresa);
        order.verify(userRepository).deactivateAllByEmpresaId("empresa-a");
        assertThat(empresa.getAtivo()).isFalse();
        assertThat(empresa.getEncerradaEm()).isNotNull();
        verify(empresaRepository, never()).delete(any());
    }

    @Test
    void pagamentoEmAndamentoInterrompeEncerramentoAntesDeDestruirConfiguracao() {
        when(empresaRepository.findByIdForUpdate("empresa-a")).thenReturn(Optional.of(empresa));
        doThrow(new ApiBusinessException(org.springframework.http.HttpStatus.CONFLICT,
                "MERCADO_PAGO_ACTIVE_PAYMENTS", "Pagamento pendente"))
                .when(mercadoPagoLifecycleService).validarSemPagamentosAtivos("empresa-a");

        assertThatThrownBy(() -> service.encerrarDefinitivamente(admin))
                .isInstanceOf(ApiBusinessException.class);

        verify(mercadoPagoLifecycleService, never()).desvincularParaEncerramento(any());
        verifyNoInteractions(pointBindingService, terminalLifecycleService, userRepository);
        assertThat(empresa.getAtivo()).isTrue();
    }
}
