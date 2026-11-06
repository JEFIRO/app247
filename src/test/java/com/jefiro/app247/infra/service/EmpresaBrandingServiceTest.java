package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.dto.EmpresaBrandingRequest;
import com.jefiro.app247.infra.repository.EmpresaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class EmpresaBrandingServiceTest {
    private final EmpresaRepository repository = mock(EmpresaRepository.class);
    private final ObjectStorageService storage = mock(ObjectStorageService.class);
    private final EmpresaService service = new EmpresaService();
    private Empresa empresa;

    @BeforeEach
    void setUp() {
        EmpresaContext.set("empresa-a");
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "objectStorageService", storage);
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

    @Test
    void enviaLogoParaPastaDaEmpresaEApagaOAnterior() {
        empresa.setLogoUrl("https://bucket/o/empresas/empresa-a/logo/antigo.png");
        MultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", new byte[]{1});
        when(storage.uploadImage(file, "empresas/empresa-a/logo"))
                .thenReturn("https://bucket/o/empresas/empresa-a/logo/novo.png");

        var response = service.enviarLogo(file, false);

        assertThat(response.logoUrl()).isEqualTo("https://bucket/o/empresas/empresa-a/logo/novo.png");
        assertThat(empresa.getLogoDarkUrl()).isNull();
        verify(storage).deleteAfterCommit("https://bucket/o/empresas/empresa-a/logo/antigo.png");
    }

    @Test
    void logoEscuroNaoAlteraOLogoPrincipal() {
        empresa.setLogoUrl("https://cdn.example/claro.png");
        MultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", new byte[]{1});
        when(storage.uploadImage(any(), any())).thenReturn("https://bucket/o/escuro.png");

        var response = service.enviarLogo(file, true);

        assertThat(response.logoDarkUrl()).isEqualTo("https://bucket/o/escuro.png");
        assertThat(response.logoUrl()).isEqualTo("https://cdn.example/claro.png");
    }

    @Test
    void removerLogoLimpaAUrlEApagaOArquivo() {
        empresa.setLogoUrl("https://bucket/o/logo.png");

        var response = service.removerLogo(false);

        assertThat(response.logoUrl()).isNull();
        verify(storage).deleteAfterCommit("https://bucket/o/logo.png");
    }

    @Test
    void trocarLogoPorLinkApagaOArquivoAntigoMasManterNaoApaga() {
        empresa.setLogoUrl("https://bucket/o/logo.png");

        service.atualizarBranding(new EmpresaBrandingRequest(
                "Mercado", "https://bucket/o/logo.png", null, "#112233", "#445566", "#778899"));
        verify(storage, never()).deleteAfterCommit(any());

        service.atualizarBranding(new EmpresaBrandingRequest(
                "Mercado", "https://cdn.example/outro.png", null, "#112233", "#445566", "#778899"));
        verify(storage).deleteAfterCommit("https://bucket/o/logo.png");
    }
}
