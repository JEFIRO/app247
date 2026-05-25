package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.Pagamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PagamentoRepository extends JpaRepository<Pagamento, String> {
    Optional<Pagamento> findByTransactionId(String paymentId);
}
