package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.EstoqueCondominio;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface EstoqueCondominioRepository extends JpaRepository<EstoqueCondominio, String> {
    List<EstoqueCondominio> findAllByCondominioIdCondominioAndCondominioEmpresaId(
            String condominioId, String empresaId);
    Optional<EstoqueCondominio> findByCondominioIdCondominioAndProdutoIdProduto(
            String condominioId, String produtoId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EstoqueCondominio e where e.condominio.idCondominio=:condominioId and e.produto.idProduto=:produtoId")
    Optional<EstoqueCondominio> findForUpdate(@Param("condominioId") String condominioId,
                                              @Param("produtoId") String produtoId);

    @Query("select e.produto.idProduto, e.produto.nome, sum(e.quantidade) from EstoqueCondominio e " +
           "where e.condominio.empresa.id=:empresaId group by e.produto.idProduto, e.produto.nome")
    List<Object[]> somarPorProdutoDaEmpresa(@Param("empresaId") String empresaId);
}
