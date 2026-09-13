package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.ImportacaoProdutoItem;
import com.jefiro.app247.domain.model.enum_type.ImportacaoProdutoItemStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ImportacaoProdutoItemRepository extends JpaRepository<ImportacaoProdutoItem, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from ImportacaoProdutoItem item where item.id=:id")
    java.util.Optional<ImportacaoProdutoItem> findForUpdate(@Param("id") String id);

    List<ImportacaoProdutoItem> findAllByImportacaoIdOrderByAbaAscLinhaAsc(String importacaoId);
    List<ImportacaoProdutoItem> findAllByImportacaoIdAndStatusOrderByLinhaAsc(
            String importacaoId, ImportacaoProdutoItemStatus status);
    List<ImportacaoProdutoItem> findAllByImportacaoIdAndStatusAndAbaOrderByLinhaAsc(
            String importacaoId, ImportacaoProdutoItemStatus status, String aba);
    long countByImportacaoIdAndStatus(String importacaoId, ImportacaoProdutoItemStatus status);
}
