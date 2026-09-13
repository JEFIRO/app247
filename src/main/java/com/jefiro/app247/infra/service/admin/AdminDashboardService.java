package com.jefiro.app247.infra.service.admin;

import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.dto.admin.AdminDashboardResponse;
import com.jefiro.app247.domain.model.dto.admin.AdminStockSummaryResponse;
import com.jefiro.app247.domain.model.dto.EmpresaBrandingResponse;
import com.jefiro.app247.infra.repository.EmpresaRepository;
import com.jefiro.app247.infra.service.EmpresaContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AdminDashboardService {
    @Autowired private EmpresaRepository empresaRepository;
    @Autowired private AdminSalesQueryService salesService;
    @Autowired private AdminTerminalQueryService terminalService;
    @Autowired private AdminAlertQueryService alertService;
    @Autowired private AdminPaymentQueryService paymentService;
    @Autowired private AdminOnboardingService onboardingService;
    @Autowired private AdminActivityQueryService activityService;

    @Transactional(readOnly = true)
    public AdminDashboardResponse dashboard() {
        String empresaId = EmpresaContext.require();
        String empresaNome = empresaRepository.findById(empresaId)
                .map(EmpresaBrandingResponse::from)
                .map(EmpresaBrandingResponse::nomeExibicao)
                .orElse("Empresa");

        return new AdminDashboardResponse(
                empresaNome,
                currentUserName(),
                Instant.now(),
                salesService.today(),
                terminalService.summary(),
                alertService.summary(),
                AdminStockSummaryResponse.regraIndisponivel(),
                paymentService.attention(),
                onboardingService.status(),
                activityService.recent(8)
        );
    }

    private String currentUserName() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user.getNome();
        }
        return null;
    }
}
