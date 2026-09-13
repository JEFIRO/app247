package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.TerminalTelemetryHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface TerminalTelemetryHistoryRepository extends JpaRepository<TerminalTelemetryHistory, String> {
    List<TerminalTelemetryHistory> findByTerminalIdTerminalAndCapturedAtGreaterThanEqualOrderByCapturedAtAsc(
            String terminalId, Instant since);
    @Modifying
    @Query("delete from TerminalTelemetryHistory h where h.receivedAt < :cutoff")
    int deleteByReceivedAtBefore(Instant cutoff);
}
