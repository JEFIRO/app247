package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.PaymentAttempt;
import com.jefiro.app247.domain.model.dto.admin.AdminPaymentResponse;
import com.jefiro.app247.domain.model.enum_type.PagamentoStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import com.jefiro.app247.domain.model.enum_type.PaymentProvider;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Collection;

public interface PagamentoRepository extends JpaRepository<PaymentAttempt, String> {
    @Query("""
            select count(a) from PaymentAttempt a
            where a.empresa.id = :empresaId
              and not exists (
                  select newer.idPagamento from PaymentAttempt newer
                  where newer.order = a.order and newer.attemptNumber > a.attemptNumber
              )
              and (
                  a.status = :actionRequired
                  or (a.status = :pending and a.updatedAt < :staleBefore)
                  or (a.status = :failed and lower(coalesce(a.statusDetail, '')) = :processingError)
              )
            """)
    long countAttention(@Param("empresaId") String empresaId,
                        @Param("staleBefore") Instant staleBefore,
                        @Param("pending") PagamentoStatus pending,
                        @Param("actionRequired") PagamentoStatus actionRequired,
                        @Param("failed") PagamentoStatus failed,
                        @Param("processingError") String processingError);

    @Query(value = """
            select new com.jefiro.app247.domain.model.dto.admin.AdminPaymentResponse(
                a.idPagamento, a.order.idOrder, a.attemptNumber, a.provider, a.status,
                a.statusDetail, a.valor, a.createdAt, a.updatedAt,
                a.order.terminal.idTerminal, a.order.terminal.nome
            )
            from PaymentAttempt a
            where a.empresa.id = :empresaId
              and a.createdAt >= :from
              and a.createdAt < :to
              and (:status is null or a.status = :status)
              and (:provider is null or a.provider = :provider)
              and (:terminalId is null or a.order.terminal.idTerminal = :terminalId)
              and (:orderId is null or a.order.idOrder = :orderId)
            """,
            countQuery = """
            select count(a) from PaymentAttempt a
            where a.empresa.id = :empresaId
              and a.createdAt >= :from
              and a.createdAt < :to
              and (:status is null or a.status = :status)
              and (:provider is null or a.provider = :provider)
              and (:terminalId is null or a.order.terminal.idTerminal = :terminalId)
              and (:orderId is null or a.order.idOrder = :orderId)
            """)
    Page<AdminPaymentResponse> findAdminPayments(
            @Param("empresaId") String empresaId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("status") PagamentoStatus status,
            @Param("provider") PaymentProvider provider,
            @Param("terminalId") String terminalId,
            @Param("orderId") String orderId,
            Pageable pageable);

    Optional<PaymentAttempt> findByTransactionId(String paymentId);
    Optional<PaymentAttempt> findByProviderOrderId(String providerOrderId);
    Optional<PaymentAttempt> findByProviderAndExternalReference(PaymentProvider provider, String externalReference);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from PaymentAttempt a join fetch a.order o where a.provider=:provider and a.externalReference=:externalReference")
    Optional<PaymentAttempt> findForTransition(@Param("provider") PaymentProvider provider,
                                               @Param("externalReference") String externalReference);
    java.util.List<PaymentAttempt> findByOrderIdOrderOrderByAttemptNumberAsc(String orderId);
    boolean existsByEmpresaIdAndStatusIn(String empresaId, Collection<PagamentoStatus> statuses);
}
