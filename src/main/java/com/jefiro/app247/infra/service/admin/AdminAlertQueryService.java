package com.jefiro.app247.infra.service.admin;

import com.jefiro.app247.domain.model.dto.admin.AdminAlertResponse;
import com.jefiro.app247.domain.model.dto.admin.AdminAlertSummaryResponse;
import com.jefiro.app247.domain.model.enum_type.TelemetryAlertStatus;
import com.jefiro.app247.domain.model.enum_type.TelemetryAlertType;
import com.jefiro.app247.domain.model.enum_type.TerminalOperationalStatus;
import com.jefiro.app247.infra.repository.TerminalTelemetryAlertRepository;
import com.jefiro.app247.infra.service.EmpresaContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminAlertQueryService {
    @Autowired private TerminalTelemetryAlertRepository alertRepository;

    @Transactional(readOnly = true)
    public AdminAlertSummaryResponse summary() {
        String empresaId = EmpresaContext.require();
        long active = alertRepository.countByTerminalCondominioEmpresaIdAndStatus(
                empresaId, TelemetryAlertStatus.ACTIVE);
        long critical = alertRepository.countCritical(
                empresaId, TelemetryAlertStatus.ACTIVE, TelemetryAlertType.TERMINAL_OFFLINE,
                List.of(TerminalOperationalStatus.CRITICO, TerminalOperationalStatus.OFFLINE));
        return new AdminAlertSummaryResponse(active, 0, Math.max(0, active - critical), critical);
    }

    @Transactional(readOnly = true)
    public Page<AdminAlertResponse> list(TelemetryAlertStatus status, Pageable pageable) {
        int size = Math.min(Math.max(pageable.getPageSize(), 1), 100);
        Sort sort = pageable.getSort().isSorted()
                ? pageable.getSort()
                : Sort.by(Sort.Direction.DESC, "openedAt");
        if (sort.stream().anyMatch(order -> !List.of("openedAt", "lastObservedAt", "status", "type")
                .contains(order.getProperty()))) {
            throw new IllegalArgumentException("Ordenação de alertas inválida");
        }
        return alertRepository.findAdminAlerts(EmpresaContext.require(), status,
                PageRequest.of(Math.max(pageable.getPageNumber(), 0), size, sort));
    }
}
