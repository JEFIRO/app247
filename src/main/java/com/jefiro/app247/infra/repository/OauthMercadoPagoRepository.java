package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.MercadoPagoConta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;
import com.jefiro.app247.domain.model.enum_type.MercadoPagoAccountBindingStatus;

public interface OauthMercadoPagoRepository extends JpaRepository<MercadoPagoConta,String> {
    Optional<MercadoPagoConta> findFirstByEmpresaIdOrderByLinkedAtDesc(String empresaId);
    List<MercadoPagoConta> findAllByEmpresaIdOrderByLinkedAtAsc(String empresaId);
    Optional<MercadoPagoConta> findFirstByEmpresaIdAndStatusOrderByLinkedAtDesc(
            String empresaId, MercadoPagoAccountBindingStatus status);
}
