package com.jefiro.app247.infra.repository;
import com.jefiro.app247.domain.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface OrderItemRepository extends JpaRepository<OrderItem,String>{
    List<OrderItem> findAllByOrderIdOrder(String orderId);
}
