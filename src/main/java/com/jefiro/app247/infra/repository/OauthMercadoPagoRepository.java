package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.MercadoPagoConta;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.List;
import com.jefiro.app247.domain.model.enum_type.MercadoPagoAccountBindingStatus;

public interface OauthMercadoPagoRepository extends JpaRepository<MercadoPagoConta,String> {
    Optional<MercadoPagoConta> findFirstByEmpresaIdOrderByLinkedAtDesc(String empresaId);
    List<MercadoPagoConta> findAllByEmpresaIdOrderByLinkedAtAsc(String empresaId);
    Optional<MercadoPagoConta> findFirstByEmpresaIdAndStatusOrderByLinkedAtDesc(
            String empresaId, MercadoPagoAccountBindingStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from MercadoPagoConta c join fetch c.empresa where c.idMercadoConta=:id")
    Optional<MercadoPagoConta> findByIdForUpdate(@Param("id") String id);

    /** Contas vinculadas (lease ativo) cujo access token vence até {@code limite}. */
    @Query("select c.idMercadoConta from MercadoPagoContaAtiva a join a.conta c"
            + " where c.status=:status and c.refreshToken is not null"
            + " and c.dataExpiracao is not null and c.dataExpiracao<=:limite order by c.dataExpiracao")
    List<String> findIdsParaRenovar(@Param("status") MercadoPagoAccountBindingStatus status,
                                    @Param("limite") Instant limite);
}
