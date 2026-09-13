package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.ImportacaoProduto;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ImportacaoProdutoRepository extends JpaRepository<ImportacaoProduto, String> {
    List<ImportacaoProduto> findAllByStatusOrderByCreatedAtAsc(
            com.jefiro.app247.domain.model.enum_type.ImportacaoProdutoStatus status, Pageable pageable);

    Optional<ImportacaoProduto> findByIdAndEmpresaId(String id, String empresaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from ImportacaoProduto i where i.id=:id and i.empresa.id=:empresaId")
    Optional<ImportacaoProduto> findForUpdate(@Param("id") String id, @Param("empresaId") String empresaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from ImportacaoProduto i where i.id=:id")
    Optional<ImportacaoProduto> findForUpdateById(@Param("id") String id);
}
