package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Condominio;
import com.jefiro.app247.domain.model.EstoqueCondominio;
import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.Produto;
import com.jefiro.app247.domain.model.enum_type.ProdutoSyncOperation;
import com.jefiro.app247.domain.model.terminal.Terminal;
import com.jefiro.app247.infra.repository.EstoqueCondominioRepository;
import com.jefiro.app247.infra.repository.TerminalRepository;
import com.jefiro.app247.infra.repository.PromocaoProdutoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProdutoSyncServiceTest {
    @Mock TerminalRepository terminalRepository;
    @Mock EstoqueCondominioRepository estoqueRepository;
    @Mock PromocaoProdutoRepository promocaoProdutoRepository;
    @Mock PricingService pricingService;
    @InjectMocks ProdutoSyncService service;

    @BeforeEach
    void setupPricing() {
        lenient().when(promocaoProdutoRepository.findProductIdsWithTemporalTransition(
                anyString(), anyString(), any(), any())).thenReturn(List.of());
        lenient().when(pricingService.calcular(any(), any(), any())).thenAnswer(call -> {
            Produto produto = call.getArgument(0);
            BigDecimal valor = MoneyPolicy.persistence(produto.getPreco());
            return new com.jefiro.app247.domain.model.dto.PrecoCalculado(
                    produto, valor, valor, MoneyPolicy.persistence(BigDecimal.ZERO), null);
        });
    }

    @Test
    void fullSyncRetornaSomenteCatalogoAtualComoUpsert() {
        var estoque = estoque(true, true);
        when(terminalRepository.findById("terminal-a")).thenReturn(Optional.of(terminal()));
        when(estoqueRepository.findCurrentCatalog("cond-a")).thenReturn(List.of(estoque));

        var response = service.sincronizar("terminal-a", Optional.empty());

        assertTrue(response.fullSync());
        assertEquals(ProdutoSyncOperation.UPSERT, response.changes().get(0).operation());
        assertEquals("prod-a", response.changes().get(0).produto().id());
        verify(estoqueRepository, never()).findCatalogChanges(anyString(), any(), any());
    }

    @Test
    void fullSyncVazioEhValidoParaCondominioSemAssociacoes() {
        when(terminalRepository.findById("terminal-a")).thenReturn(Optional.of(terminal()));
        when(estoqueRepository.findCurrentCatalog("cond-a")).thenReturn(List.of());

        var response = service.sincronizar("terminal-a", Optional.empty());

        assertTrue(response.fullSync());
        assertTrue(response.changes().isEmpty());
    }

    @Test
    void incrementalRetornaRemocaoQuandoAssociacaoFoiDesativada() {
        var estoque = estoque(false, true);
        when(terminalRepository.findById("terminal-a")).thenReturn(Optional.of(terminal()));
        when(estoqueRepository.findCatalogChanges(eq("cond-a"), any(), any()))
                .thenReturn(List.of(estoque));

        var response = service.sincronizar("terminal-a", Optional.of(Instant.parse("2026-08-24T16:00:00Z")));

        assertFalse(response.fullSync());
        assertEquals(ProdutoSyncOperation.REMOVE, response.changes().get(0).operation());
        assertEquals("prod-a", response.changes().get(0).productId());
        assertNull(response.changes().get(0).produto());
    }

    @Test
    void incrementalRetornaProdutoAtualizado() {
        var estoque = estoque(true, true);
        estoque.getProduto().setPreco(BigDecimal.valueOf(8));
        when(terminalRepository.findById("terminal-a")).thenReturn(Optional.of(terminal()));
        when(estoqueRepository.findCatalogChanges(eq("cond-a"), any(), any()))
                .thenReturn(List.of(estoque));

        var response = service.sincronizar("terminal-a", Optional.of(Instant.parse("2026-08-24T16:00:00Z")));

        assertEquals(ProdutoSyncOperation.UPSERT, response.changes().get(0).operation());
        assertEquals(new BigDecimal("8.000000"), response.changes().get(0).produto().preco());
    }

    @Test
    void incrementalRetornaRemocaoQuandoProdutoFoiDesativadoGlobalmente() {
        var estoque = estoque(true, false);
        when(terminalRepository.findById("terminal-a")).thenReturn(Optional.of(terminal()));
        when(estoqueRepository.findCatalogChanges(eq("cond-a"), any(), any()))
                .thenReturn(List.of(estoque));

        var response = service.sincronizar("terminal-a", Optional.of(Instant.parse("2026-08-24T16:00:00Z")));

        assertEquals(ProdutoSyncOperation.REMOVE, response.changes().get(0).operation());
        assertEquals("prod-a", response.changes().get(0).productId());
    }

    private Terminal terminal() {
        Empresa empresa = new Empresa();
        empresa.setId("empresa-a");
        Condominio condominio = new Condominio();
        condominio.setIdCondominio("cond-a");
        condominio.setEmpresa(empresa);
        Terminal terminal = new Terminal();
        terminal.setIdTerminal("terminal-a");
        terminal.setCondominio(condominio);
        return terminal;
    }

    private EstoqueCondominio estoque(boolean estoqueAtivo, boolean produtoAtivo) {
        Produto produto = new Produto();
        produto.setIdProduto("prod-a");
        produto.setCodigo("789");
        produto.setPreco(BigDecimal.valueOf(7));
        produto.setStatus(produtoAtivo);
        produto.setCreateAt(LocalDateTime.of(2026, 8, 24, 15, 0));
        produto.setUpdateAt(LocalDateTime.of(2026, 8, 24, 16, 30));
        EstoqueCondominio estoque = new EstoqueCondominio();
        estoque.setProduto(produto);
        estoque.setAtivo(estoqueAtivo);
        estoque.setQuantidade(BigDecimal.valueOf(-1));
        estoque.setUpdatedAt(LocalDateTime.of(2026, 8, 24, 16, 20));
        return estoque;
    }
}
