package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.*;
import com.jefiro.app247.domain.model.dto.CarrinhoRequest;
import com.jefiro.app247.domain.model.dto.ItemRequest;
import com.jefiro.app247.domain.model.terminal.Terminal;
import com.jefiro.app247.infra.repository.CarrinhoRepository;
import com.jefiro.app247.infra.repository.TerminalRepository;
import com.jefiro.app247.domain.model.enum_type.UnidadeMedida;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CarrinhoServiceTest {
    @Mock CarrinhoRepository repository;
    @Mock ProdutoService produtoService;
    @Mock TerminalRepository terminalRepository;
    @InjectMocks CarrinhoService service;

    @Test
    void salvaItensComPrecoSnapshotETenantDoTerminal() {
        Empresa empresa = Empresa.builder().id("empresa-a").build();
        Terminal terminal = terminal(empresa);
        Produto produto = produto("produto-a", empresa, "7.50");
        when(terminalRepository.findById("terminal-a")).thenReturn(Optional.of(terminal));
        when(produtoService.buscarPorIdDoTenant("produto-a", "empresa-a")).thenReturn(produto);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Carrinho carrinho = service.save(new CarrinhoRequest("terminal-a",
                List.of(new ItemRequest("produto-a", 2, null))));
        produto.setPreco(new BigDecimal("99.00"));

        assertEquals(empresa, carrinho.getEmpresa());
        assertEquals(new BigDecimal("15.00"), carrinho.getSubtotal());
        assertEquals(new BigDecimal("7.50"), carrinho.getItems().get(0).getUnitPrice());
        assertEquals(UnidadeMedida.UN, carrinho.getItems().get(0).getUnidadeMedida());
        assertEquals(carrinho, carrinho.getItems().get(0).getCarrinho());
        assertEquals(produto, carrinho.getItems().get(0).getProduto());
    }

    @Test
    void rejeitaProdutoDuplicadoNoMesmoCarrinho() {
        Empresa empresa = Empresa.builder().id("empresa-a").build();
        when(terminalRepository.findById("terminal-a")).thenReturn(Optional.of(terminal(empresa)));
        when(produtoService.buscarPorIdDoTenant("produto-a", "empresa-a"))
                .thenReturn(produto("produto-a", empresa, "5.00"));
        CarrinhoRequest request = new CarrinhoRequest("terminal-a", List.of(
                new ItemRequest("produto-a", 1, null), new ItemRequest("produto-a", 1, null)));

        assertThrows(IllegalArgumentException.class, () -> service.save(request));
        verify(repository, never()).save(any());
    }

    @Test
    void rejeitaCarrinhoVazioAntesDePersistir() {
        assertThrows(IllegalArgumentException.class,
                () -> service.save(new CarrinhoRequest("terminal-a", List.of())));
        verifyNoInteractions(terminalRepository, produtoService, repository);
    }

    @Test
    void rejeitaSubtotalDivergenteAntesDoPagamento() {
        Empresa empresa = Empresa.builder().id("empresa-a").build();
        Terminal terminal = terminal(empresa);
        Produto produto = produto("produto-a", empresa, "7.50");
        Item item = new Item(produto, 2, null);
        item.setEmpresa(empresa);
        Carrinho carrinho = new Carrinho();
        carrinho.setEmpresa(empresa);
        carrinho.setTerminal(terminal);
        carrinho.addItem(item);
        carrinho.setSubtotal(new BigDecimal("10.00"));

        assertThrows(IllegalStateException.class, () -> service.validarParaPagamento(carrinho));
    }

    private Terminal terminal(Empresa empresa) {
        Condominio condominio = new Condominio();
        condominio.setIdCondominio("cond-a");
        condominio.setEmpresa(empresa);
        Terminal terminal = new Terminal();
        terminal.setIdTerminal("terminal-a");
        terminal.setCondominio(condominio);
        return terminal;
    }

    private Produto produto(String id, Empresa empresa, String preco) {
        Produto produto = new Produto();
        produto.setIdProduto(id);
        produto.setEmpresa(empresa);
        produto.setCodigo("COD");
        produto.setNome("Leite");
        produto.setPreco(new BigDecimal(preco));
        produto.setUnidadeMedida(UnidadeMedida.UN);
        return produto;
    }
}
