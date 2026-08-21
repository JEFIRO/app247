package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.dto.OrderDTO;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, String> {
    @Query("""
                SELECT new com.jefiro.app247.domain.model.dto.OrderDTO(
                    o.idOrder,
                    o.status,
                    o.subtotal,
                    o.desconto,
                    o.total,
                    o.createdAt
                )
                FROM Order o
                WHERE o.user.idUser = :userId
            """)
    Page<OrderDTO> findOrdersByUserId(@Param("userId") String userId, Pageable pageable);
    Optional<Order> findByCarrinhoIdCarrinho(String carrinhoId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.idOrder = :orderId")
    Optional<Order> findByIdForUpdate(@Param("orderId") String orderId);
}
