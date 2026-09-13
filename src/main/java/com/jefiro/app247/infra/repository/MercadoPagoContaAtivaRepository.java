package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.MercadoPagoContaAtiva;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MercadoPagoContaAtivaRepository extends JpaRepository<MercadoPagoContaAtiva, String> {
    @Query("select a from MercadoPagoContaAtiva a join fetch a.conta c join fetch a.empresa where a.empresa.id=:empresaId")
    Optional<MercadoPagoContaAtiva> findByEmpresaId(@Param("empresaId") String empresaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from MercadoPagoContaAtiva a join fetch a.conta c join fetch a.empresa where a.empresa.id=:empresaId")
    Optional<MercadoPagoContaAtiva> findByEmpresaIdForUpdate(@Param("empresaId") String empresaId);

    @Query("select a from MercadoPagoContaAtiva a join fetch a.conta c join fetch a.empresa where a.mpUserId=:mpUserId")
    Optional<MercadoPagoContaAtiva> findWithBindingByMpUserId(@Param("mpUserId") String mpUserId);
}
