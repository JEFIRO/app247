package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.InventarioItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InventarioItemRepository extends JpaRepository<InventarioItem, String> {
    @EntityGraph(attributePaths = {"produto", "produto.codigosBarras", "estoqueEmpresa", "estoqueCondominio"})
    List<InventarioItem> findAllByInventarioIdAndEmpresaIdOrderByProdutoCodigoInterno(String inventarioId, String empresaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventarioItem i join fetch i.produto where i.id=:id and i.inventario.id=:inventarioId and i.empresa.id=:empresaId")
    Optional<InventarioItem> findForUpdate(@Param("id") String id,
                                           @Param("inventarioId") String inventarioId,
                                           @Param("empresaId") String empresaId);
}
