package com.jefiro.app247.infra.service.admin;

import com.jefiro.app247.domain.model.dto.admin.AdminTerminalSummaryResponse;
import com.jefiro.app247.domain.model.enum_type.TelemetryAlertStatus;
import com.jefiro.app247.infra.repository.TerminalRepository;
import com.jefiro.app247.infra.repository.TerminalTelemetryAlertRepository;
import com.jefiro.app247.infra.service.EmpresaContext;
import com.jefiro.app247.infra.service.TelemetryThresholds;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AdminTerminalQueryService {
    @Autowired private TerminalRepository terminalRepository;
    @Autowired private TerminalTelemetryAlertRepository alertRepository;
    @Autowired private TelemetryThresholds thresholds;

    @Transactional(readOnly = true)
    public AdminTerminalSummaryResponse summary() {
        String empresaId = EmpresaContext.require();
        long total = terminalRepository.countByCondominioEmpresaIdAndAtivoTrue(empresaId);
        Instant onlineAfter = Instant.now().minusSeconds(thresholds.getOfflineSeconds());
        long online = terminalRepository
                .countByCondominioEmpresaIdAndAtivoTrueAndLastPingGreaterThanEqual(empresaId, onlineAfter);
        long withAlert = alertRepository.countTerminalsWithAlerts(empresaId, TelemetryAlertStatus.ACTIVE);
        return new AdminTerminalSummaryResponse(total, online, Math.max(0, total - online), withAlert);
    }
}
