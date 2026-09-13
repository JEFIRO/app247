package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.TerminalTelemetryAlert;
import com.jefiro.app247.domain.model.enum_type.TelemetryAlertStatus;
import com.jefiro.app247.domain.model.enum_type.TelemetryAlertType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.time.Instant;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import com.jefiro.app247.domain.model.dto.admin.AdminAlertResponse;
import com.jefiro.app247.domain.model.enum_type.TerminalOperationalStatus;

public interface TerminalTelemetryAlertRepository extends JpaRepository<TerminalTelemetryAlert, String> {
    long countByTerminalCondominioEmpresaIdAndStatus(
            String empresaId, TelemetryAlertStatus status);

    @Query("""
            select count(distinct a.terminal.idTerminal)
            from TerminalTelemetryAlert a
            where a.terminal.condominio.empresa.id = :empresaId
              and a.status = :status
            """)
    long countTerminalsWithAlerts(@Param("empresaId") String empresaId,
                                  @Param("status") TelemetryAlertStatus status);

    @Query("""
            select count(a)
            from TerminalTelemetryAlert a
            where a.terminal.condominio.empresa.id = :empresaId
              and a.status = :status
              and (
                  a.type = :offlineType
                  or exists (
                      select current.terminalId from TerminalTelemetryCurrent current
                      where current.terminal = a.terminal
                        and current.operationalStatus in :criticalStatuses
                  )
              )
            """)
    long countCritical(@Param("empresaId") String empresaId,
                       @Param("status") TelemetryAlertStatus status,
                       @Param("offlineType") TelemetryAlertType offlineType,
                       @Param("criticalStatuses") java.util.Collection<TerminalOperationalStatus> criticalStatuses);

    @Query(value = """
            select new com.jefiro.app247.domain.model.dto.admin.AdminAlertResponse(
                a.id, a.terminal.idTerminal, a.terminal.nome,
                a.terminal.condominio.idCondominio, a.terminal.condominio.nome,
                a.type, a.status, current.operationalStatus, a.message,
                a.openedAt, a.lastObservedAt, a.resolvedAt
            )
            from TerminalTelemetryAlert a
            left join TerminalTelemetryCurrent current on current.terminal = a.terminal
            where a.terminal.condominio.empresa.id = :empresaId
              and (:status is null or a.status = :status)
            """,
            countQuery = """
            select count(a) from TerminalTelemetryAlert a
            where a.terminal.condominio.empresa.id = :empresaId
              and (:status is null or a.status = :status)
            """)
    Page<AdminAlertResponse> findAdminAlerts(
            @Param("empresaId") String empresaId,
            @Param("status") TelemetryAlertStatus status,
            Pageable pageable);

    Optional<TerminalTelemetryAlert> findByTerminalIdTerminalAndTypeAndStatus(
            String terminalId, TelemetryAlertType type, TelemetryAlertStatus status);
    List<TerminalTelemetryAlert> findByTerminalIdTerminalAndStatusOrderByOpenedAtDesc(
            String terminalId, TelemetryAlertStatus status);
    List<TerminalTelemetryAlert> findTop100ByTerminalIdTerminalOrderByOpenedAtDesc(String terminalId);
    @Modifying
    @Query("delete from TerminalTelemetryAlert a where a.status=com.jefiro.app247.domain.model.enum_type.TelemetryAlertStatus.RESOLVED and a.resolvedAt<:cutoff")
    int deleteResolvedBefore(Instant cutoff);
}
