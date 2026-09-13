package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.TransferenciaEstoque;
import com.jefiro.app247.domain.model.enum_type.StatusTransferenciaEstoque;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TransferenciaEstoqueRepository extends JpaRepository<TransferenciaEstoque, String> {
    @EntityGraph(attributePaths={"itens","itens.produto","createdBy"})
    Optional<TransferenciaEstoque> findByIdAndEmpresaId(String id, String empresaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select distinct t from TransferenciaEstoque t left join fetch t.itens i left join fetch i.produto where t.id=:id and t.empresa.id=:empresaId")
    Optional<TransferenciaEstoque> findForUpdate(@Param("id") String id, @Param("empresaId") String empresaId);

    @EntityGraph(attributePaths={"createdBy"})
    Page<TransferenciaEstoque> findAllByEmpresaId(String empresaId, Pageable pageable);

    @EntityGraph(attributePaths={"createdBy"})
    Page<TransferenciaEstoque> findAllByEmpresaIdAndStatus(String empresaId,
                                                           StatusTransferenciaEstoque status,
                                                           Pageable pageable);
}
