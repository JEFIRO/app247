package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, String> {
}
