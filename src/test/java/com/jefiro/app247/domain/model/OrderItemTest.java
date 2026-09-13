package com.jefiro.app247.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderItemTest {

    @Test
    void deveRecuperarCodigoInternoDoProdutoAntesDePersistir() {
        Produto produto = new Produto();
        produto.setCodigoInterno("PROD-123");

        OrderItem item = new OrderItem();
        item.setProduto(produto);

        item.prePersist();

        assertEquals("PROD-123", item.getCodigoInterno());
    }

    @Test
    void deveRejeitarPersistenciaSemCodigoInternoDisponivel() {
        OrderItem item = new OrderItem();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                item::prePersist);

        assertEquals(
                "OrderItem sem código interno e sem produto válido para recuperar o snapshot",
                exception.getMessage());
    }
}
