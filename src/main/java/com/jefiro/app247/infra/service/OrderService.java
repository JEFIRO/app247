package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Carrinho;
import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.dto.OrderDTO;
import com.jefiro.app247.domain.model.enum_type.CarrinhoStatus;
import com.jefiro.app247.domain.model.enum_type.OriginRequest;
import com.jefiro.app247.infra.event.MercadoPagoCobrancaEvent;
import com.jefiro.app247.infra.repository.OrderRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

@Service
public class OrderService {

    @Autowired
    OrderRepository repository;
    @Autowired
    CarrinhoService carrinhoService;
    @Autowired
    UserService userService;
    @Autowired
    ApplicationEventPublisher eventPublisher;

    @Transactional
    public Order createOrder(String carrinhoId, String id_user) {

        Carrinho carrinho = carrinhoService.getById(carrinhoId);

        if (carrinho.getStatus() != CarrinhoStatus.OPEN) {
            throw new RuntimeException("Carrinho já finalizado");
        }

        carrinho.setStatus(CarrinhoStatus.READY_FOR_PAYMENT);

        carrinhoService.save(carrinho);

        Order order = new Order(carrinho);

        if (id_user != null) {
            User user = userService.getUser(id_user);

            System.out.println("ID: " + user.getIdUser());

            order.setUser(user);
        }

        return repository.save(order);
    }

    @Transactional
    public void criarCobranca(Carrinho carrinho) {
        try {
            Order order = new Order(carrinho);
            order.setOriginRequest(OriginRequest.TERMINAL);

            order = repository.save(order);

            eventPublisher.publishEvent(new MercadoPagoCobrancaEvent(order));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }


    public Order getOrder(String id_order) {
        return repository.findById(id_order).orElseThrow(() -> new NoSuchElementException("Order não existe"));
    }

    public Page<OrderDTO> getOrderByUser(Long user_id, Pageable pageable) {
        return repository.findOrdersByUserId(user_id, pageable);
    }


    public Order save(Order order) {
        return repository.save(order);
    }
}
