package com.jefiro.app247.infra.repository;
import com.jefiro.app247.domain.model.PaymentEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface PaymentEventRepository extends JpaRepository<PaymentEvent,String>{
    List<PaymentEvent> findAllByPaymentAttemptIdPagamentoOrderByOccurredAtAsc(String attemptId);
}
