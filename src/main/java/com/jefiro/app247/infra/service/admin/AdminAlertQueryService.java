package com.jefiro.app247.infra.service.admin;

import com.jefiro.app247.domain.model.dto.admin.AdminAlertResponse;
import com.jefiro.app247.domain.model.dto.admin.AdminAlertSummaryResponse;
import com.jefiro.app247.domain.model.enum_type.TelemetryAlertStatus;
import com.jefiro.app247.domain.model.enum_type.TelemetryAlertType;
import com.jefiro.app247.domain.model.enum_type.TerminalOperationalStatus;
import com.jefiro.app247.infra.repository.TerminalTelemetryAlertRepository;
import com.jefiro.app247.infra.service.EmpresaContext;
import com.jefiro.app247.infra.service.PagePolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class AdminAlertQueryService {
    private static final Set<String> SORTS = Set.of("openedAt", "lastObservedAt", "status", "type");

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
        return alertRepository.findAdminAlerts(EmpresaContext.require(), status,
                PagePolicy.sanitize(pageable, Sort.by(Sort.Direction.DESC, "openedAt"), SORTS,
                        "Ordenação de alertas inválida"));
    }
}
