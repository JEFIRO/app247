package com.jefiro.app247.infra.service.admin;

import com.jefiro.app247.domain.model.dto.admin.AdminPaymentAttentionResponse;
import com.jefiro.app247.domain.model.dto.admin.AdminPaymentResponse;
import com.jefiro.app247.domain.model.enum_type.PagamentoStatus;
import com.jefiro.app247.domain.model.enum_type.PaymentProvider;
import com.jefiro.app247.infra.repository.PagamentoRepository;
import com.jefiro.app247.infra.service.EmpresaContext;
import com.jefiro.app247.infra.service.TimePolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;

@Service
public class AdminPaymentQueryService {
    private static final Set<String> SORTS = Set.of("createdAt", "updatedAt", "status", "valor");

    @Autowired private PagamentoRepository pagamentoRepository;
    @Autowired private AdminPeriodService periodService;

    @Value("${admin.dashboard.payment-pending-attention-minutes:15}")
    private long pendingAttentionMinutes;

    @Transactional(readOnly = true)
    public AdminPaymentAttentionResponse attention() {
        Instant staleBefore = Instant.now().minus(Duration.ofMinutes(pendingAttentionMinutes));
        long count = pagamentoRepository.countAttention(
                EmpresaContext.require(), staleBefore, PagamentoStatus.PENDING,
                PagamentoStatus.ACTION_REQUIRED, PagamentoStatus.FAILED, "processing_error");
        return new AdminPaymentAttentionResponse(count, staleBefore);
    }

    @Transactional(readOnly = true)
    public Page<AdminPaymentResponse> list(LocalDate from, LocalDate to,
                                           String status, String provider,
                                           String terminalId, String orderId,
                                           Pageable pageable) {
        TimePolicy.DateRange range = periodService.resolve("30D", from, to, Instant.now());
        return pagamentoRepository.findAdminPayments(
                EmpresaContext.require(), range.from(), range.to(), paymentStatus(status),
                paymentProvider(provider), blank(terminalId), blank(orderId), sanitize(pageable));
    }

    private PagamentoStatus paymentStatus(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return PagamentoStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Status de pagamento inválido");
        }
    }

    private PaymentProvider paymentProvider(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return PaymentProvider.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Provedor de pagamento inválido");
        }
    }

    private Pageable sanitize(Pageable pageable) {
        int size = Math.min(Math.max(pageable.getPageSize(), 1), 100);
        Sort sort = pageable.getSort().isSorted() ? pageable.getSort() : Sort.by(Sort.Direction.DESC, "createdAt");
        if (sort.stream().anyMatch(order -> !SORTS.contains(order.getProperty()))) {
            throw new IllegalArgumentException("Ordenação de pagamentos inválida");
        }
        return PageRequest.of(Math.max(pageable.getPageNumber(), 0), size, sort);
    }

    private String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
