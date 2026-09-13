package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.terminal.Terminal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.List;
import java.time.Instant;

public interface TerminalRepository extends JpaRepository<Terminal, String> {
    Optional<Terminal> findBySerialNumber(String serialNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from terminal t where t.idTerminal = :id")
    Optional<Terminal> findByIdForTelemetryUpdate(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from terminal t where t.idTerminal = :id")
    Optional<Terminal> findByIdForPaymentUpdate(@Param("id") String id);

    List<Terminal> findAllByCondominioIdCondominioAndCondominioEmpresaIdOrderByNome(
            String condominioId, String empresaId);

    List<Terminal> findAllByCondominioEmpresaIdOrderByNome(String empresaId);

    @EntityGraph(attributePaths = {"condominio", "condominio.empresa"})
    Optional<Terminal> findByIdTerminalAndCondominioEmpresaId(String terminalId, String empresaId);

    Optional<Terminal> findByMercadoPagoTerminalId(String mercadoPagoTerminalId);

    List<Terminal> findAllByCondominioEmpresaIdAndMercadoPagoTerminalIdIsNotNull(String empresaId);

    long countByCondominioEmpresaIdAndMercadoPagoTerminalIdIsNotNull(String empresaId);

    long countByCondominioEmpresaId(String empresaId);

    long countByCondominioEmpresaIdAndAtivoTrue(String empresaId);

    long countByCondominioEmpresaIdAndAtivoTrueAndLastPingGreaterThanEqual(
            String empresaId, Instant onlineAfter);

    boolean existsByCondominioEmpresaIdAndAtivoTrue(String empresaId);

    boolean existsByCondominioEmpresaIdAndAtivoTrueAndMercadoPagoTerminalIdIsNotNull(
            String empresaId);

    @Query("select t.idTerminal from terminal t where t.condominio.idCondominio in :condominiumIds")
    List<String> findIdsByCondominiumIds(@Param("condominiumIds") java.util.Collection<String> condominiumIds);
}
