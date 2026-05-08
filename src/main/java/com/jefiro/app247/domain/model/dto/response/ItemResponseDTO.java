package com.jefiro.app247.domain.model.dto.response;

import com.jefiro.app247.domain.model.Item;
import com.jefiro.app247.domain.model.enum_type.ItemStatus;

import java.math.BigDecimal;

public record ItemResponseDTO(

        String itemId,

        Long productId,

        String barcode,

        String name,

        BigDecimal unitPrice,

        Integer quantity,

        Boolean requiresWeight,

        BigDecimal expectedWeight,

        BigDecimal receivedWeight,

        ItemStatus status

) {
    public ItemResponseDTO(Item i) {
        this(i.getItemId(), i.getProdutoId(), i.getBarcode(), i.getName(), i.getUnitPrice(), i.getQuantity(),
                i.getRequiresWeight(), i.getExpectedWeight(), i.getReceivedWeight(), i.getStatus());
    }
}