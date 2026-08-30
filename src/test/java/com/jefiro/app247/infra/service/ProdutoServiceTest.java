package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.dto.CreateProductDTO;
import com.jefiro.app247.domain.model.Produto;
import com.jefiro.app247.domain.model.enum_type.ProdutoCatalogChangeReason;
import com.jefiro.app247.infra.event.ProdutoCatalogChangedEvent;
import com.jefiro.app247.infra.repository.EstoqueCondominioRepository;
import com.jefiro.app247.infra.repository.ProdutoRepository;
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
    @Mock FileStorageService fileStorageService;
    @Mock EmpresaService empresaService;
    @Mock EstoqueCondominioRepository estoqueRepository;
    @Mock ApplicationEventPublisher eventPublisher;
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
        when(produtoRepository.existsByCodigoAndEmpresaId("789", "empresa-a")).thenReturn(true);

        assertThatThrownBy(() -> service.salvar(dto, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT")
                .hasMessageContaining("Este código de produto já está em uso");
        verifyNoInteractions(fileStorageService, empresaService);
    }

    @Test
    void atualizacaoComCodigoDeOutroProdutoRetornaConflitoAntesDeSalvarImagem() {
        CreateProductDTO dto = new CreateProductDTO(
                "789", "Produto", BigDecimal.TEN, null, "UN", "OUTROS",
                "Descrição", null, BigDecimal.ONE, BigDecimal.ZERO);
        when(produtoRepository.findByIdProdutoAndEmpresaId("produto-a", "empresa-a"))
                .thenReturn(Optional.of(new Produto(dto)));
        when(produtoRepository.existsByCodigoAndEmpresaIdAndIdProdutoNot(
                "789", "empresa-a", "produto-a")).thenReturn(true);

        assertThatThrownBy(() -> service.atualizar("produto-a", dto, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT")
                .hasMessageContaining("Este código de produto já está em uso");
        verifyNoInteractions(fileStorageService, empresaService);
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
