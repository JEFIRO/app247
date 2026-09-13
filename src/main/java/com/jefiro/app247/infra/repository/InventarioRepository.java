package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.Inventario;
import com.jefiro.app247.domain.model.enum_type.InventarioStatus;
import com.jefiro.app247.domain.model.enum_type.TipoLocalEstoque;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InventarioRepository extends JpaRepository<Inventario, String> {
    Optional<Inventario> findByIdAndEmpresaId(String id, String empresaId);
    @Query("select i from Inventario i where i.empresa.id=:empresaId " +
            "and (:status is null or i.status=:status) and (:localTipo is null or i.localTipo=:localTipo)")
    Page<Inventario> pesquisar(@Param("empresaId") String empresaId,
                               @Param("status") InventarioStatus status,
                               @Param("localTipo") TipoLocalEstoque localTipo,
                               Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventario i where i.id=:id and i.empresa.id=:empresaId")
    Optional<Inventario> findForUpdate(@Param("id") String id, @Param("empresaId") String empresaId);
}
