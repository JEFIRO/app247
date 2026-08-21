package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.terminal.Terminal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface TerminalRepository extends JpaRepository<Terminal, String> {
    Optional<Terminal> findBySerialNumber(String serialNumber);

    List<Terminal> findAllByCondominioIdCondominioAndCondominioEmpresaIdOrderByNome(
            String condominioId, String empresaId);

    Optional<Terminal> findByIdTerminalAndCondominioEmpresaId(String terminalId, String empresaId);

    Optional<Terminal> findByMercadoPagoTerminalId(String mercadoPagoTerminalId);

    List<Terminal> findAllByCondominioEmpresaIdAndMercadoPagoTerminalIdIsNotNull(String empresaId);

    long countByCondominioEmpresaId(String empresaId);
}
