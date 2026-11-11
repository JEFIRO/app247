package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.NotaFiscalEntrada;
import com.jefiro.app247.domain.model.enum_type.NotaFiscalEntradaStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NotaFiscalEntradaRepository extends JpaRepository<NotaFiscalEntrada, String> {
    Optional<NotaFiscalEntrada> findByIdAndEmpresaId(String id, String empresaId);
    Optional<NotaFiscalEntrada> findFirstByEmpresaIdAndChaveAndStatusOrderByCreatedAtDesc(
            String empresaId, String chave, NotaFiscalEntradaStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from NotaFiscalEntrada n where n.id=:id and n.empresa.id=:empresaId")
    Optional<NotaFiscalEntrada> findForUpdate(@Param("id") String id, @Param("empresaId") String empresaId);
}
