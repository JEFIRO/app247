package com.jefiro.app247.domain.model.dto.response;

import com.jefiro.app247.domain.model.Carrinho;
import com.jefiro.app247.domain.model.enum_type.CarrinhoStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CarrinhoResponseDTO(

        String carrinhoId,

        String terminalId,

        CarrinhoStatus status,

        BigDecimal subtotal,

        List<ItemResponseDTO> items,

        LocalDateTime createdAt,

        LocalDateTime updatedAt

) {
    public CarrinhoResponseDTO(Carrinho car) {
        this(car.getIdCarrinho(), car.getIdTerminal(), car.getStatus(), car.getSubtotal(), car.getItems().stream().map(ItemResponseDTO::new).toList(), car.getCreatedAt(), car.getUpdatedAt());
    }
}