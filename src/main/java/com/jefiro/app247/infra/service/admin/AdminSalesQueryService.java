package com.jefiro.app247.infra.service.admin;

import com.jefiro.app247.domain.model.dto.admin.AdminSaleResponse;
import com.jefiro.app247.domain.model.dto.admin.AdminSalesSummaryResponse;
import com.jefiro.app247.domain.model.enum_type.order.OrderStatus;
import com.jefiro.app247.infra.repository.OrderRepository;
import com.jefiro.app247.infra.service.EmpresaContext;
import com.jefiro.app247.infra.service.MoneyPolicy;
import com.jefiro.app247.infra.service.TimePolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;

@Service
public class AdminSalesQueryService {
    private static final Set<String> SORTS = Set.of("createdAt", "paidAt", "totalCobrado", "status");

    @Autowired private OrderRepository orderRepository;
    @Autowired private AdminPeriodService periodService;

    @Transactional(readOnly = true)
    public AdminSalesSummaryResponse summary(String period, LocalDate from, LocalDate to) {
        return summary(periodService.resolve(period, from, to, Instant.now()));
    }

    @Transactional(readOnly = true)
    public AdminSalesSummaryResponse today() {
        return summary(TimePolicy.today(Instant.now()));
    }

    public AdminSalesSummaryResponse summary(TimePolicy.DateRange range) {
        OrderRepository.SalesAggregation row = orderRepository.summarizeSales(
                EmpresaContext.require(), OrderStatus.PROCESSED, range.from(), range.to());
        long count = row == null || row.getQuantidadeVendas() == null
                ? 0L : row.getQuantidadeVendas();
        BigDecimal revenue = row == null || row.getFaturamento() == null
                ? BigDecimal.ZERO : row.getFaturamento();
        revenue = MoneyPolicy.persistence(revenue);
        BigDecimal average = count == 0
                ? MoneyPolicy.persistence(BigDecimal.ZERO)
                : MoneyPolicy.persistence(revenue.divide(BigDecimal.valueOf(count), 6, RoundingMode.HALF_UP));
        return new AdminSalesSummaryResponse(range.from(), range.to(), count, revenue, average);
    }

    @Transactional(readOnly = true)
    public Page<AdminSaleResponse> list(String period, LocalDate from, LocalDate to,
                                        String condominioId, String terminalId,
                                        String status, Pageable pageable) {
        TimePolicy.DateRange range = periodService.resolve(
                period == null || period.isBlank() ? "30D" : period,
                from, to, Instant.now());
        OrderStatus parsedStatus = orderStatus(status);
        return orderRepository.findAdminSales(
                EmpresaContext.require(), range.from(), range.to(), blank(condominioId), blank(terminalId),
                parsedStatus, parsedStatus == OrderStatus.PROCESSED, sanitize(pageable));
    }

    private OrderStatus orderStatus(String value) {
        if (value == null || value.isBlank()) return null;
        OrderStatus byValue = OrderStatus.findByValue(value.trim());
        if (byValue != null) return byValue;
        try {
            return OrderStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Status de venda inválido");
        }
    }

    private Pageable sanitize(Pageable pageable) {
        int size = Math.min(Math.max(pageable.getPageSize(), 1), 100);
        Sort sort = pageable.getSort().isSorted() ? pageable.getSort() : Sort.by(Sort.Direction.DESC, "createdAt");
        if (sort.stream().anyMatch(order -> !SORTS.contains(order.getProperty()))) {
            throw new IllegalArgumentException("Ordenação de vendas inválida");
        }
        return PageRequest.of(Math.max(pageable.getPageNumber(), 0), size, sort);
    }

    private String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
