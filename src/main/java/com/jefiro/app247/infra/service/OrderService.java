package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Carrinho;
import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.Pagamento;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.dto.OrderDTO;
import com.jefiro.app247.domain.model.enum_type.CarrinhoStatus;
import com.jefiro.app247.domain.model.enum_type.OriginRequest;
import com.jefiro.app247.infra.event.MercadoPagoCobrancaEvent;
import com.jefiro.app247.infra.event.OrderReservadaEvent;
import com.jefiro.app247.infra.repository.OrderRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.util.Optional;

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
            throw new IllegalStateException("Carrinho já finalizado");
        }
        if (repository.findByCarrinhoIdCarrinho(carrinhoId).isPresent()) {
            throw new IllegalStateException("Carrinho já possui Order");
        }

        carrinhoService.reprecificarParaCheckout(carrinho);

        carrinho.setStatus(CarrinhoStatus.READY_FOR_PAYMENT);

        carrinhoService.save(carrinho);

        Order order = new Order(carrinho);

        if (id_user != null) {
            User user = userService.getUser(id_user);
            if (user.getEmpresa() == null || carrinho.getEmpresa() == null
                    || !user.getEmpresa().getId().equals(carrinho.getEmpresa().getId())) {
                throw new IllegalArgumentException("Usuário e carrinho pertencem a empresas diferentes");
            }

            order.setUser(user);
        }

        order = repository.saveAndFlush(order);
        eventPublisher.publishEvent(new OrderReservadaEvent(order));
        return order;
    }

    @Transactional
    public Order createOrderTest(String carrinhoId, String id_user) {

        Carrinho carrinho = carrinhoService.getById(carrinhoId);
        Order order = new Order(carrinho);

        if (id_user != null) {
            User user = userService.getUser(id_user);
            if (user.getEmpresa() == null || carrinho.getEmpresa() == null
                    || !user.getEmpresa().getId().equals(carrinho.getEmpresa().getId())) {
                throw new IllegalArgumentException("Usuário e carrinho pertencem a empresas diferentes");
            }

            order.setUser(user);
        }

        Pagamento pagamento = new Pagamento(order);
        order.setPagamento(pagamento);

        return repository.save(order);
    }


    @Transactional
    public Order criarCobranca(Carrinho carrinho) {
        carrinhoService.validarParaPagamento(carrinho);
        carrinhoService.reprecificarParaCheckout(carrinho);
        Order order = repository.findByCarrinhoIdCarrinho(carrinho.getIdCarrinho())
                .orElseGet(() -> createOrder(carrinho.getIdCarrinho(), null));
        if (order.getCarrinho() != null) {
            order.atualizarTotaisDoCarrinho();
            repository.save(order);
        }
        if (order.getMpOrderId() != null) {
            return order;
        }
        carrinho.setStatus(CarrinhoStatus.PAYMENT_PENDING);
        carrinhoService.save(carrinho);

        eventPublisher.publishEvent(new MercadoPagoCobrancaEvent(order));
        return order;
    }


    public Order getOrder(String id_order) {
        return repository.findById(id_order).orElseThrow(() -> new NoSuchElementException("Order não existe"));
    }

    public Optional<Order> findByCarrinho(String carrinhoId) {
        return repository.findByCarrinhoIdCarrinho(carrinhoId);
    }

    public Order getOrderForUpdate(String idOrder) {
        return repository.findByIdForUpdate(idOrder)
                .orElseThrow(() -> new NoSuchElementException("Order não existe"));
    }

    public Order getOrderForReconciliation(String idOrder) {
        return repository.findByIdForReconciliation(idOrder)
                .orElseThrow(() -> new NoSuchElementException("Order não existe"));
    }

    public Order getOrderForTerminal(String idOrder, String terminalId) {
        Order order = getOrder(idOrder);
        String orderTerminalId = order.getCarrinho() != null
                ? order.getCarrinho().getIdTerminal() : order.getIdTerminal();
        if (terminalId == null || !terminalId.equals(orderTerminalId)) {
            throw new NoSuchElementException("Order não existe para o terminal informado");
        }
        return order;
    }

    public Page<OrderDTO> getOrderByUser(String user_id, Pageable pageable) {
        return repository.findOrdersByUserId(user_id, pageable);
    }


    public Order save(Order order) {
        return repository.save(order);
    }
}
