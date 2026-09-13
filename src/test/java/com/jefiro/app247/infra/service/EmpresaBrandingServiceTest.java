package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.dto.EmpresaBrandingRequest;
import com.jefiro.app247.infra.repository.EmpresaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class EmpresaBrandingServiceTest {
    private final EmpresaRepository repository = mock(EmpresaRepository.class);
    private final EmpresaService service = new EmpresaService();
    private Empresa empresa;

    @BeforeEach
    void setUp() {
        EmpresaContext.set("empresa-a");
        ReflectionTestUtils.setField(service, "repository", repository);
        empresa = Empresa.builder()
                .id("empresa-a")
                .razaoSocial("Mercado Exemplo Ltda")
                .nomeFantasia("Mercado Exemplo")
                .build();
        when(repository.findById("empresa-a")).thenReturn(Optional.of(empresa));
        when(repository.save(any(Empresa.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        EmpresaContext.clear();
    }

    @Test
    void usaNomeDaEmpresaECoresPadraoQuandoBrandingAindaNaoFoiConfigurado() {
        var response = service.branding();

        assertThat(response.nomeExibicao()).isEqualTo("Mercado Exemplo");
        assertThat(response.corPrincipal()).isEqualTo("#169DFF");
        assertThat(response.corSecundaria()).isEqualTo("#62C8FF");
        assertThat(response.corDestaque()).isEqualTo("#00D084");
    }

    @Test
    void atualizaSomenteAEmpresaDoContextoSemReceberEmpresaId() {
        var response = service.atualizarBranding(new EmpresaBrandingRequest(
                "Mercado Parque", "https://cdn.example/logo.png", null,
                "#112233", "#445566", "#778899"));

        assertThat(response.nomeExibicao()).isEqualTo("Mercado Parque");
        assertThat(response.logoUrl()).isEqualTo("https://cdn.example/logo.png");
        assertThat(empresa.getCorPrincipal()).isEqualTo("#112233");
        verify(repository).findById("empresa-a");
        verify(repository).save(empresa);
    }
}
