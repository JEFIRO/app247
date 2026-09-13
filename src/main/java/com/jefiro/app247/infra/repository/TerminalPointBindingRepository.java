package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.TerminalPointBinding;
import com.jefiro.app247.domain.model.enum_type.TerminalPointBindingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TerminalPointBindingRepository extends JpaRepository<TerminalPointBinding, String> {
    Optional<TerminalPointBinding> findFirstByTerminalIdTerminalAndStatusOrderByLinkedAtDesc(
            String terminalId, TerminalPointBindingStatus status);

    List<TerminalPointBinding> findAllByMercadoPagoContaIdMercadoContaAndStatus(
            String mercadoPagoContaId, TerminalPointBindingStatus status);

    List<TerminalPointBinding> findAllByEmpresaIdAndStatus(
            String empresaId, TerminalPointBindingStatus status);
}
