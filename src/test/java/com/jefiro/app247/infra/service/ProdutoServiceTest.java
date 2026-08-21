package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.dto.CreateProductDTO;
import com.jefiro.app247.infra.repository.ProdutoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProdutoServiceTest {
    @Mock ProdutoRepository produtoRepository;
    @Mock FileStorageService fileStorageService;
    @Mock EmpresaService empresaService;
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
}
