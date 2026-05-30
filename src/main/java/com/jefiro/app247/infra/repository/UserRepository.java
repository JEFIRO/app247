package com.jefiro.app247.infra.repository;


import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.dto.OrderDTO;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    UserDetails findByCpf(String cpf);

    @Query("SELECT u FROM User u WHERE u.cpf = :cpf")
    Optional<User> getByCpf(@Param("cpf") String cpf);

    boolean existsByCpf(String cpf);

  @Query("""
    SELECT new com.jefiro.app247.domain.model.dto.OrderDTO(
        o.orderId,
        o.status,
        o.subtotal,
        o.desconto,
        o.total,
        o.createdAt
    )
    FROM User u
    JOIN u.orders o
    WHERE u.userId = :userId
""")
Page<OrderDTO> findOrdersByUserId(@Param("userId") Long userId, Pageable pageable);

}
