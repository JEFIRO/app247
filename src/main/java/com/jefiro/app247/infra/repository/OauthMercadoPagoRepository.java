package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.MercadoPagoConta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OauthMercadoPagoRepository extends JpaRepository<MercadoPagoConta,String> {
    Optional<MercadoPagoConta> findByMpUserId(String mpUserId);

    Optional<MercadoPagoConta> findByEmpresa_Id(String empresaId);
}
