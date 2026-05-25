package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Carrinho;
import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.enum_type.CarrinhoStatus;
import com.jefiro.app247.infra.repository.OrderRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Transactional
    public Order createOrder(String carrinhoId, Long id_user) {

        Carrinho carrinho = carrinhoService.getById(carrinhoId);

        if (carrinho.getStatus() != CarrinhoStatus.OPEN) {
            throw new RuntimeException("Carrinho já finalizado");
        }

        carrinho.setStatus(CarrinhoStatus.READY_FOR_PAYMENT);

        carrinhoService.save(carrinho);

        Order order = new Order(carrinho);
        
        if (id_user != null) {
            User user = userService.getUser(id_user);
            order.setUser(user);
        }

        return repository.save(order);
    }

    public Order getOrder(String id_order) {
        return repository.findById(id_order).orElseThrow(() -> new NoSuchElementException("Order não existe"));
    }

}
