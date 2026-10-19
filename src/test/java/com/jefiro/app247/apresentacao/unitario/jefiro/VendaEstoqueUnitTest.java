package com.jefiro.app247.apresentacao.unitario.jefiro;

import com.jefiro.app247.domain.model.*;
import com.jefiro.app247.domain.model.enum_type.TipoMovimentacaoEstoque;
import com.jefiro.app247.domain.model.enum_type.UnidadeMedida;
import com.jefiro.app247.domain.model.terminal.Terminal;
import com.jefiro.app247.infra.repository.EstoqueCondominioRepository;
import com.jefiro.app247.infra.repository.MovimentacaoEstoqueRepository;
import com.jefiro.app247.infra.service.EstoqueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Teste unitário do fluxo de venda no {@link EstoqueService}: o checkout reserva
 * (baixa) o estoque do condomínio, o pagamento aprovado confirma a venda e o
 * cancelamento devolve as unidades. As movimentações gravadas são "lembradas" em
 * memória para simular a idempotência do repositório.
 */
// @ExtendWith(MockitoExtension.class): liga o Mockito ao JUnit 5. É ele que cria os objetos
// marcados com @Mock e @InjectMocks antes de cada teste e confere, ao final, se algum
// when(...) configurado ficou sem uso (stub desnecessário faz o teste falhar).
@ExtendWith(MockitoExtension.class)
class VendaEstoqueUnitTest {

    // @Mock: cria um objeto FALSO (dublê) da classe. Ele não executa o código real nem toca
    // banco/rede; por padrão todo método devolve null, false, 0 ou vazio. O teste diz o que
    // ele deve responder com when(...).thenReturn(...) e depois confere com verify(...) se foi
    // chamado (e quantas vezes). Serve para testar UMA classe isolada das outras.
    //
    // @InjectMocks: cria um objeto REAL da classe testada e injeta nele os @Mock acima
    // (pelo construtor ou pelos campos). Assim o código do serviço roda de verdade, mas
    // tudo o que ele chama por fora é falso e controlado pelo teste.

    // Simula o estoque do condomínio; findForUpdate é a busca com lock pessimista usada na baixa.
    @Mock
    EstoqueCondominioRepository estoqueRepository;
    // Simula o histórico de movimentações e a checagem de chave de idempotência.
    @Mock
    MovimentacaoEstoqueRepository movimentacaoRepository;
    // Classe testada de verdade: recebe os mocks acima no lugar das dependências reais.
    @InjectMocks
    EstoqueService estoqueService;

    private final Set<String> chavesGravadas = new HashSet<>();
    private EstoqueCondominio estoque;
    private Order pedido;

    @BeforeEach
    void cenario() {
        // Estoque inicial: 10 unidades; pedido de 2 unidades do mesmo produto.

        Fixture f = fixture("10.000", "2.000");
        estoque = f.estoque();
        pedido = f.order();

        // lenient(): esses when(...) valem para todos os testes, mas nem todo teste usa todos;

        //Normalmente, o Mockito pode reclamar quando você configura um comportamento com when(...).thenReturn(...),
        // mas esse comportamento não é utilizado durante o teste.

        //Com lenient(), essa configuração específica deixa de gerar essa reclamação.
        // sem lenient o Mockito acusaria "stub desnecessário" e falharia o teste.

        lenient().when(estoqueRepository.findForUpdate("cond-a", "prod-a")).thenReturn(Optional.of(estoque));

        lenient().when(movimentacaoRepository.existsByChaveIdempotencia(anyString()))
                .thenAnswer(inv -> chavesGravadas.contains(inv.<String>getArgument(0)));

        // thenAnswer: Simula o save: devolve o mesmo User que recebeu (sem isso o mock devolveria null).
        lenient().when(movimentacaoRepository.save(any(MovimentacaoEstoque.class))).thenAnswer(inv -> {
            MovimentacaoEstoque m = inv.getArgument(0);
            chavesGravadas.add(m.getChaveIdempotencia());
            return m;
        });

    }

    @Test
    @DisplayName("Venda: reserva baixa o estoque e a confirmação do pagamento mantém a baixa")
    void vendaBaixaEstoque() {
        estoqueService.reservar(pedido);

        // assertThat(valor): função do AssertJ que inicia uma verificação (assertiva) sobre um valor.
        // Ela recebe o resultado REAL obtido pelo código testado e devolve um objeto com métodos
        // encadeáveis (isEqualTo, isTrue, isFalse, isSameAs, isNull...) que descrevem o que se ESPERA
        // dele. Se a expectativa não for cumprida, o teste falha com uma mensagem clara mostrando
        // o valor esperado e o valor encontrado.

        // Leitura natural: assertThat(real).isEqualTo(esperado)  ->  "garanta que real é igual a esperado".

        assertThat(estoque.getQuantidade()).isEqualByComparingTo("8.000");

        estoqueService.confirmarVenda(pedido);
        assertThat(estoque.getQuantidade()).isEqualByComparingTo("8.000");

        ArgumentCaptor<MovimentacaoEstoque> captor = ArgumentCaptor.forClass(MovimentacaoEstoque.class);

        verify(movimentacaoRepository, times(2)).save(captor.capture());

        assertThat(captor.getAllValues()).extracting(MovimentacaoEstoque::getTipo)
                .containsExactly(TipoMovimentacaoEstoque.RESERVA, TipoMovimentacaoEstoque.VENDA);
        MovimentacaoEstoque reserva = captor.getAllValues().get(0);
        assertThat(reserva.getQuantidadeAnterior()).isEqualByComparingTo("10.000");
        assertThat(reserva.getQuantidadePosterior()).isEqualByComparingTo("8.000");
    }

    @Test
    @DisplayName("Cancelamento da compra devolve as unidades ao estoque")
    void cancelamentoDevolveEstoque() {
        estoqueService.reservar(pedido);

        estoqueService.liberar(pedido, true);

        assertThat(estoque.getQuantidade()).isEqualByComparingTo("10.000");
    }

    @Test
    @DisplayName("Confirmação repetida do pagamento não baixa o estoque duas vezes")
    void confirmacaoDuplicadaNaoBaixaDuasVezes() {
        estoqueService.reservar(pedido);
        estoqueService.reservar(pedido);
        estoqueService.confirmarVenda(pedido);
        estoqueService.confirmarVenda(pedido);

        assertThat(estoque.getQuantidade()).isEqualByComparingTo("8.000");
    }

    @Test
    @DisplayName("Venda de produto não disponível no condomínio é recusada")
    void produtoNaoDisponivel() {
        when(estoqueRepository.findForUpdate("cond-a", "prod-a")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.reservar(pedido))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Produto não disponível neste condomínio");
    }

    private static Fixture fixture(String saldo, String quantidadePedida) {
        Empresa empresa = Empresa.builder().id("empresa-a").build();
        Condominio condominio = new Condominio();
        condominio.setIdCondominio("cond-a");
        condominio.setEmpresa(empresa);
        Terminal terminal = new Terminal();
        terminal.setIdTerminal("terminal-a");
        terminal.setCondominio(condominio);
        Produto produto = new Produto();
        produto.setIdProduto("prod-a");
        produto.setEmpresa(empresa);
        produto.setCodigoInterno("SKU-A");
        CartItem item = new CartItem();
        item.setIdItem("item-a");
        item.setProduto(produto);
        item.setQuantity(new BigDecimal(quantidadePedida));
        item.setName("Refrigerante");
        item.setUnitPrice(new BigDecimal("5.000000"));
        item.setOriginalPrice(new BigDecimal("5.000000"));
        item.setCalculatedDiscount(BigDecimal.ZERO.setScale(6));
        item.setCalculatedSubtotal(new BigDecimal("10.000000"));
        item.setUnidadeMedida(UnidadeMedida.UN);
        Carrinho carrinho = new Carrinho();
        carrinho.setTerminal(terminal);
        carrinho.setItems(List.of(item));
        item.setCarrinho(carrinho);
        Order order = new Order();
        order.setIdOrder("order-a");
        order.setEmpresa(empresa);
        order.setCondominio(condominio);
        order.setTerminal(terminal);
        order.setCarrinho(carrinho);
        OrderItem orderItem = OrderItem.snapshot(order, item);
        orderItem.setId("item-a");
        order.setItems(List.of(orderItem));
        EstoqueCondominio estoque = new EstoqueCondominio();
        estoque.setCondominio(condominio);
        estoque.setProduto(produto);
        estoque.setQuantidade(new BigDecimal(saldo));
        return new Fixture(order, estoque);
    }

    private record Fixture(Order order, EstoqueCondominio estoque) {
    }
}
