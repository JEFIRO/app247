package com.jefiro.app247.infra.event;

import com.jefiro.app247.infra.service.EstoqueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class EstoqueOrderListener {
    @Autowired private EstoqueService estoqueService;

    @EventListener
    public void reservar(OrderReservadaEvent event) { estoqueService.reservar(event.order()); }
    @EventListener
    public void vender(OrderPaidEvent event) { estoqueService.confirmarVenda(event.order()); }
    @EventListener
    public void liberar(OrderNotCompletedEvent event) {
        estoqueService.liberar(event.order(), event.cancelamento());
    }
    @EventListener
    public void cancelar(CompraCanceladaEvent event) {
        estoqueService.liberar(event.getOrder(), true);
    }
}
