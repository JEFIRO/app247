package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.EstoqueCondominio;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EstoqueCondominioRepository extends JpaRepository<EstoqueCondominio, String> {
    List<EstoqueCondominio> findAllByCondominioIdCondominioAndCondominioEmpresaId(
            String condominioId, String empresaId);
    Optional<EstoqueCondominio> findByCondominioIdCondominioAndProdutoIdProduto(
            String condominioId, String produtoId);

    Optional<EstoqueCondominio> findByCondominioIdCondominioAndProdutoIdProdutoAndAtivoTrue(
            String condominioId, String produtoId);

    @Query("select e from EstoqueCondominio e join fetch e.produto p " +
            "where e.condominio.idCondominio=:condominioId and p.idProduto=:produtoId")
    Optional<EstoqueCondominio> findCatalogEntry(@Param("condominioId") String condominioId,
                                                  @Param("produtoId") String produtoId);

    @Query("select distinct e.condominio.idCondominio from EstoqueCondominio e " +
           "where e.produto.idProduto=:produtoId and e.ativo=true")
    List<String> findActiveCondominiumIdsByProductId(@Param("produtoId") String produtoId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update EstoqueCondominio e set e.updatedAt=:updatedAt " +
            "where e.ativo=true and e.condominio.idCondominio in :condominioIds " +
            "and e.produto.idProduto in :produtoIds")
    int touchCatalog(@Param("condominioIds") java.util.Collection<String> condominioIds,
                     @Param("produtoIds") java.util.Collection<String> produtoIds,
                     @Param("updatedAt") LocalDateTime updatedAt);

    @Query("select e from EstoqueCondominio e join fetch e.produto p " +
           "where e.condominio.idCondominio=:condominioId and e.ativo=true and p.status=true " +
           "order by p.codigo")
    List<EstoqueCondominio> findCurrentCatalog(@Param("condominioId") String condominioId);

    @Query("select e from EstoqueCondominio e join fetch e.produto p " +
           "where e.condominio.idCondominio=:condominioId and " +
           "((e.updatedAt>:lastSync and e.updatedAt<=:syncAt) or " +
           "(p.updateAt>:lastSync and p.updateAt<=:syncAt)) " +
           "order by p.codigo")
    List<EstoqueCondominio> findCatalogChanges(
            @Param("condominioId") String condominioId,
            @Param("lastSync") LocalDateTime lastSync,
            @Param("syncAt") LocalDateTime syncAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EstoqueCondominio e where e.condominio.idCondominio=:condominioId and e.produto.idProduto=:produtoId")
    Optional<EstoqueCondominio> findForUpdate(@Param("condominioId") String condominioId,
                                              @Param("produtoId") String produtoId);

    @Query("select e.produto.idProduto, e.produto.nome, sum(e.quantidade) from EstoqueCondominio e " +
           "where e.condominio.empresa.id=:empresaId group by e.produto.idProduto, e.produto.nome")
    List<Object[]> somarPorProdutoDaEmpresa(@Param("empresaId") String empresaId);
}
