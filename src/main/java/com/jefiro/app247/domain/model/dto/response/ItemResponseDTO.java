package com.jefiro.app247.domain.model.dto.response;

import com.jefiro.app247.domain.model.Item;
import com.jefiro.app247.domain.model.enum_type.ItemStatus;
import com.jefiro.app247.domain.model.enum_type.UnidadeMedida;

import java.math.BigDecimal;

public record ItemResponseDTO(

        String itemId,

        String productId,

        String barcode,

        String name,
        String foto,

        BigDecimal unitPrice,

        UnidadeMedida unidadeMedida,

        Integer quantity,

        Boolean requiresWeight,

        BigDecimal expectedWeight,

        BigDecimal receivedWeight,

        ItemStatus status

) {
    public ItemResponseDTO(Item i) {
        this(i.getIdItem(), i.getIdProduto(), i.getBarcode(), i.getName(), i.getFoto(), i.getUnitPrice(),
                i.getUnidadeMedida(), i.getQuantity(),
                i.getRequiresWeight(), i.getExpectedWeight(), i.getReceivedWeight(), i.getStatus());
    }
}
