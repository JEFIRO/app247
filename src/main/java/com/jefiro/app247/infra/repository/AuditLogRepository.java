package com.jefiro.app247.infra.repository;
import com.jefiro.app247.domain.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface AuditLogRepository extends JpaRepository<AuditLog,String>{
    Page<AuditLog> findAllByEmpresaIdOrderByCreatedAtDesc(String empresaId,Pageable pageable);

    Page<AuditLog> findAllByEmpresaIdAndActionInOrderByCreatedAtDesc(
            String empresaId, Collection<String> actions, Pageable pageable);
}
