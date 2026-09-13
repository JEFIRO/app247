package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.dto.OrderDTO;
import com.jefiro.app247.domain.model.dto.admin.AdminSaleResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import java.time.Instant;
import com.jefiro.app247.domain.model.enum_type.order.OrderStatus;

public interface OrderRepository extends JpaRepository<Order, String> {
    @Query("""
            select count(o) as quantidadeVendas,
                   coalesce(sum(o.totalCobrado), 0) as faturamento
            from Order o
            where o.empresa.id = :empresaId
              and o.status = :status
              and o.paidAt >= :from
              and o.paidAt < :to
            """)
    SalesAggregation summarizeSales(@Param("empresaId") String empresaId,
                                     @Param("status") OrderStatus status,
                                     @Param("from") Instant from,
                                     @Param("to") Instant to);

    interface SalesAggregation {
        Long getQuantidadeVendas();
        java.math.BigDecimal getFaturamento();
    }

    @Query(value = """
            select new com.jefiro.app247.domain.model.dto.admin.AdminSaleResponse(
                o.idOrder, o.status, o.totalCobrado, o.createdAt, o.paidAt,
                o.condominio.idCondominio, o.condominio.nome,
                o.terminal.idTerminal, o.terminal.nome
            )
            from Order o
            where o.empresa.id = :empresaId
              and (
                  (:paidPeriod = true and o.paidAt >= :from and o.paidAt < :to)
                  or (:paidPeriod = false and o.createdAt >= :from and o.createdAt < :to)
              )
              and (:condominioId is null or o.condominio.idCondominio = :condominioId)
              and (:terminalId is null or o.terminal.idTerminal = :terminalId)
              and (:status is null or o.status = :status)
            """,
            countQuery = """
            select count(o)
            from Order o
            where o.empresa.id = :empresaId
              and (
                  (:paidPeriod = true and o.paidAt >= :from and o.paidAt < :to)
                  or (:paidPeriod = false and o.createdAt >= :from and o.createdAt < :to)
              )
              and (:condominioId is null or o.condominio.idCondominio = :condominioId)
              and (:terminalId is null or o.terminal.idTerminal = :terminalId)
              and (:status is null or o.status = :status)
            """)
    Page<AdminSaleResponse> findAdminSales(
            @Param("empresaId") String empresaId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("condominioId") String condominioId,
            @Param("terminalId") String terminalId,
            @Param("status") OrderStatus status,
            @Param("paidPeriod") boolean paidPeriod,
            Pageable pageable);

    @Query("""
                SELECT new com.jefiro.app247.domain.model.dto.OrderDTO(
                    o.idOrder,
                    o.status,
                    o.subtotal,
                    o.desconto,
                    o.totalCobrado,
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

    @EntityGraph(attributePaths = {
            "empresa", "paymentAttempts", "carrinho", "carrinho.terminal",
            "carrinho.terminal.condominio", "carrinho.terminal.condominio.empresa"
    })
    @Query("select o from Order o where o.idOrder = :orderId")
    Optional<Order> findByIdForReconciliation(@Param("orderId") String orderId);

    @Query("""
            select o.idOrder from Order o
            where o.status in :statuses
              and o.createdAt >= :createdAfter
              and exists (
                  select a.idPagamento from PaymentAttempt a
                  where a.order = o
              )
            order by o.createdAt asc
            """)
    List<String> findRecentReconciliationCandidateIds(
            @Param("statuses") List<OrderStatus> statuses,
            @Param("createdAfter") Instant createdAfter,
            Pageable pageable);

    @Query("""
            select o.idOrder from Order o
            where o.terminal.idTerminal = :terminalId
              and o.status in :statuses
              and exists (
                  select a.idPagamento from PaymentAttempt a where a.order = o
              )
            order by o.createdAt asc
            """)
    List<String> findUnresolvedPaymentOrderIdsForTerminal(
            @Param("terminalId") String terminalId,
            @Param("statuses") List<OrderStatus> statuses,
            Pageable pageable);

    @Query("""
            select o.idOrder from Order o
            where o.status in :statuses
              and o.paymentAttempts is empty
            order by o.createdAt asc
            """)
    List<String> findOrphanPaymentOrderIds(
            @Param("statuses") List<OrderStatus> statuses,
            Pageable pageable);
}
