package com.jefiro.app247.infra.service.comprovante;

import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.infra.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ComprovanteSnapshotService {
    private final OrderService orderService;

    @Autowired
    public ComprovanteSnapshotService(OrderService orderService) {
        this.orderService = orderService;
    }

    @Transactional(readOnly = true)
    public ComprovanteCompraRequest montar(String orderId, String terminalId) {
        Order order = orderService.getOrderForTerminal(orderId, terminalId);
        return new ComprovanteCompraRequest(order);
    }
}
