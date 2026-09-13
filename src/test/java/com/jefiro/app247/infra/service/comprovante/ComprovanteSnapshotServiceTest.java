package com.jefiro.app247.infra.service.comprovante;

import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.infra.service.OrderService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ComprovanteSnapshotServiceTest {
    @Test
    void carregaPedidoValidandoTerminalAntesDeMontarSnapshot() {
        OrderService orderService = mock(OrderService.class);
        Order invalidOrder = new Order();
        when(orderService.getOrderForTerminal("order-id", "terminal-id"))
                .thenReturn(invalidOrder);
        ComprovanteSnapshotService service = new ComprovanteSnapshotService(orderService);

        assertThatThrownBy(() -> service.montar("order-id", "terminal-id"))
                .hasMessageContaining("pedido aprovado");
        verify(orderService).getOrderForTerminal("order-id", "terminal-id");
    }
}
