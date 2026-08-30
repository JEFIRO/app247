package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.terminal.Terminal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface TerminalRepository extends JpaRepository<Terminal, String> {
    Optional<Terminal> findBySerialNumber(String serialNumber);

    List<Terminal> findAllByCondominioIdCondominioAndCondominioEmpresaIdOrderByNome(
            String condominioId, String empresaId);

    List<Terminal> findAllByCondominioEmpresaIdOrderByNome(String empresaId);

    Optional<Terminal> findByIdTerminalAndCondominioEmpresaId(String terminalId, String empresaId);

    Optional<Terminal> findByMercadoPagoTerminalId(String mercadoPagoTerminalId);

    List<Terminal> findAllByCondominioEmpresaIdAndMercadoPagoTerminalIdIsNotNull(String empresaId);

    long countByCondominioEmpresaId(String empresaId);

    @Query("select t.idTerminal from terminal t where t.condominio.idCondominio in :condominiumIds")
    List<String> findIdsByCondominiumIds(@Param("condominiumIds") java.util.Collection<String> condominiumIds);
}
