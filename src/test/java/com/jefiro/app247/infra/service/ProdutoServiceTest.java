package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.dto.CreateProductDTO;
import com.jefiro.app247.domain.model.Produto;
import com.jefiro.app247.domain.model.enum_type.ProdutoCatalogChangeReason;
import com.jefiro.app247.infra.event.ProdutoCatalogChangedEvent;
import com.jefiro.app247.infra.repository.EstoqueCondominioRepository;
import com.jefiro.app247.infra.repository.ProdutoRepository;
import com.jefiro.app247.infra.repository.ProdutoCodigoBarrasRepository;
import com.jefiro.app247.infra.repository.ProdutoFiscalRepository;
import com.jefiro.app247.infra.repository.PerfilTributarioRepository;
import com.jefiro.app247.infra.exception.ApiBusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProdutoServiceTest {
    @Mock ProdutoRepository produtoRepository;
    @Mock ProdutoCodigoBarrasRepository codigoBarrasRepository;
    @Mock ProdutoFiscalRepository produtoFiscalRepository;
    @Mock PerfilTributarioRepository perfilTributarioRepository;
    @Mock FileStorageService fileStorageService;
    @Mock EmpresaService empresaService;
    @Mock EstoqueCondominioRepository estoqueRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock AuditLogService auditLogService;
    @InjectMocks ProdutoService service;

    @BeforeEach
    void setUp() {
        EmpresaContext.set("empresa-a");
    }

    @AfterEach
    void tearDown() {
        EmpresaContext.clear();
    }

    @Test
    void codigoDuplicadoRetornaConflitoAntesDeSalvarImagem() {
        CreateProductDTO dto = new CreateProductDTO(
                "789", "Produto", BigDecimal.TEN, null, "UN", "OUTROS",
                "Descrição", null, BigDecimal.ONE, BigDecimal.ZERO);
        when(produtoRepository.existsByCodigoInternoAndEmpresaId("789", "empresa-a")).thenReturn(true);

        assertThatThrownBy(() -> service.salvar(dto, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT")
                .hasMessageContaining("Código interno já utilizado");
        verifyNoInteractions(fileStorageService, empresaService);
    }

    @Test
    void atualizacaoComCodigoDeOutroProdutoRetornaConflitoAntesDeSalvarImagem() {
        CreateProductDTO dto = new CreateProductDTO(
                "789", "Produto", BigDecimal.TEN, null, "UN", "OUTROS",
                "Descrição", null, BigDecimal.ONE, BigDecimal.ZERO);
        when(produtoRepository.findByIdProdutoAndEmpresaId("produto-a", "empresa-a"))
                .thenReturn(Optional.of(new Produto(dto)));
        when(produtoRepository.existsByCodigoInternoAndEmpresaIdAndIdProdutoNot(
                "789", "empresa-a", "produto-a")).thenReturn(true);

        assertThatThrownBy(() -> service.atualizar("produto-a", dto, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT")
                .hasMessageContaining("Código interno já utilizado");
        verifyNoInteractions(fileStorageService, empresaService);
    }

    @Test
    void codigoDeBarrasDuplicadoRetornaCodigoEstruturado() {
        CreateProductDTO dto = new CreateProductDTO(
                "7894900011517", "Produto", BigDecimal.TEN, null,
                "UN", "OUTROS", null, null, BigDecimal.ONE, BigDecimal.ZERO);
        when(codigoBarrasRepository.existsByEmpresaIdAndCodigoBarras(
                "empresa-a", "7894900011517")).thenReturn(true);

        assertThatThrownBy(() -> service.salvar(dto, null))
                .isInstanceOfSatisfying(ApiBusinessException.class, error -> {
                    org.assertj.core.api.Assertions.assertThat(error.getCode())
                            .isEqualTo("BARCODE_ALREADY_EXISTS");
                    org.assertj.core.api.Assertions.assertThat(error.getReason())
                            .isEqualTo("Código de barras já está associado a outro produto.");
                });
        verifyNoInteractions(fileStorageService, empresaService);
    }

    @Test
    void fiscalEBarcodeInativoSaoIntegradosNaCriacao() throws Exception {
        var fiscal = new CreateProductDTO.ProdutoFiscalRequest(
                "22021000", "0300500", "0", "UN", BigDecimal.ONE,
                "7894900011517", null);
        var dto = new CreateProductDTO(
                null, "Produto", BigDecimal.TEN, null, "UN", "OUTROS",
                "Descrição", null, BigDecimal.ONE, BigDecimal.ZERO, "SKU-1",
                List.of(
                        new CreateProductDTO.CodigoBarrasRequest("7894900011517", "EAN", true, true),
                        new CreateProductDTO.CodigoBarrasRequest("7894900011524", "EAN", false, false)),
                fiscal, true);
        var empresa = new com.jefiro.app247.domain.model.Empresa();
        empresa.setId("empresa-a");
        when(empresaService.getEmpresa("empresa-a")).thenReturn(empresa);
        when(produtoRepository.saveAndFlush(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    Produto produto = invocation.getArgument(0);
                    produto.setIdProduto("produto-a");
                    return produto;
                });

        Produto salvo = service.salvar(dto, null);

        org.assertj.core.api.Assertions.assertThat(salvo.getCodigosBarras()).hasSize(2);
        org.assertj.core.api.Assertions.assertThat(salvo.getCodigosBarras())
                .filteredOn(codigo -> !codigo.getAtivo())
                .singleElement().extracting(com.jefiro.app247.domain.model.ProdutoCodigoBarras::getCodigoBarras)
                .isEqualTo("7894900011524");
        org.assertj.core.api.Assertions.assertThat(salvo.getFiscal().getNcm()).isEqualTo("22021000");
        org.assertj.core.api.Assertions.assertThat(salvo.getFiscal().getProduto()).isSameAs(salvo);
    }

    @Test
    void produtoAtualizadoPublicaCondominiosOndeEstaDisponivel() throws Exception {
        CreateProductDTO dto = dto("789", BigDecimal.valueOf(8));
        Produto produto = new Produto(dto("789", BigDecimal.valueOf(7)));
        produto.setIdProduto("produto-a");
        when(produtoRepository.findByIdProdutoAndEmpresaId("produto-a", "empresa-a"))
                .thenReturn(Optional.of(produto));
        when(estoqueRepository.findActiveCondominiumIdsByProductId("produto-a"))
                .thenReturn(List.of("cond-a", "cond-b"));
        when(produtoRepository.saveAndFlush(produto)).thenReturn(produto);

        service.atualizar("produto-a", dto, null);

        var captor = org.mockito.ArgumentCaptor.forClass(ProdutoCatalogChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().reason())
                .isEqualTo(ProdutoCatalogChangeReason.PRODUCT_UPDATED);
        org.assertj.core.api.Assertions.assertThat(captor.getValue().condominiumIds())
                .containsExactlyInAnyOrder("cond-a", "cond-b");
    }

    @Test
    void produtoSemDisponibilidadePublicaEventoSemCondominios() throws Exception {
        Produto produto = new Produto(dto("789", BigDecimal.valueOf(7)));
        produto.setIdProduto("produto-a");
        when(produtoRepository.findByIdProdutoAndEmpresaId("produto-a", "empresa-a"))
                .thenReturn(Optional.of(produto));
        when(estoqueRepository.findActiveCondominiumIdsByProductId("produto-a")).thenReturn(List.of());
        when(produtoRepository.saveAndFlush(produto)).thenReturn(produto);

        service.atualizar("produto-a", dto("789", BigDecimal.valueOf(8)), null);

        var captor = org.mockito.ArgumentCaptor.forClass(ProdutoCatalogChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().condominiumIds()).isEmpty();
    }

    @Test
    void desativacaoGlobalPublicaTodosOsCondominiosAtivos() {
        Produto produto = new Produto(dto("789", BigDecimal.valueOf(7)));
        produto.setIdProduto("produto-a");
        when(produtoRepository.findByIdProdutoAndEmpresaId("produto-a", "empresa-a"))
                .thenReturn(Optional.of(produto));
        when(estoqueRepository.findActiveCondominiumIdsByProductId("produto-a"))
                .thenReturn(List.of("cond-a", "cond-b"));
        when(produtoRepository.saveAndFlush(produto)).thenReturn(produto);

        service.alterarDisponibilidade("produto-a", false);

        var captor = org.mockito.ArgumentCaptor.forClass(ProdutoCatalogChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().reason())
                .isEqualTo(ProdutoCatalogChangeReason.PRODUCT_DEACTIVATED);
        org.assertj.core.api.Assertions.assertThat(captor.getValue().condominiumIds())
                .containsExactlyInAnyOrder("cond-a", "cond-b");
    }

    private CreateProductDTO dto(String codigo, BigDecimal preco) {
        return new CreateProductDTO(codigo, "Produto", preco, null, "UN", "OUTROS",
                "Descrição", null, BigDecimal.ONE, BigDecimal.ZERO);
    }
}
