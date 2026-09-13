package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.EstoqueEmpresa;
import com.jefiro.app247.domain.model.enum_type.ProdutoCategoria;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface EstoqueEmpresaRepository extends JpaRepository<EstoqueEmpresa, String> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"produto", "produto.codigosBarras"})
    List<EstoqueEmpresa> findAllByEmpresaIdAndAtivoTrueOrderByProdutoCodigoInterno(String empresaId);
    Optional<EstoqueEmpresa> findByEmpresaIdAndProdutoIdProduto(String empresaId, String produtoId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EstoqueEmpresa e join fetch e.produto p where e.empresa.id=:empresaId and p.idProduto=:produtoId")
    Optional<EstoqueEmpresa> findForUpdate(@Param("empresaId") String empresaId,
                                           @Param("produtoId") String produtoId);

    @Query(value = """
            select e from EstoqueEmpresa e join e.produto p
            where e.empresa.id=:empresaId
              and (:ativo is null or e.ativo=:ativo)
              and (:categoria is null or p.categoria=:categoria)
              and (:busca is null or lower(p.nome) like lower(concat('%',:busca,'%'))
                   or lower(p.codigoInterno) like lower(concat('%',:busca,'%'))
                   or exists (select b.id from ProdutoCodigoBarras b
                              where b.produto=p and b.ativo=true and b.codigoBarras like concat('%',:busca,'%')))
            """,
            countQuery = """
            select count(e) from EstoqueEmpresa e join e.produto p
            where e.empresa.id=:empresaId
              and (:ativo is null or e.ativo=:ativo)
              and (:categoria is null or p.categoria=:categoria)
              and (:busca is null or lower(p.nome) like lower(concat('%',:busca,'%'))
                   or lower(p.codigoInterno) like lower(concat('%',:busca,'%'))
                   or exists (select b.id from ProdutoCodigoBarras b
                              where b.produto=p and b.ativo=true and b.codigoBarras like concat('%',:busca,'%')))
            """)
    Page<EstoqueEmpresa> pesquisar(@Param("empresaId") String empresaId,
                                   @Param("busca") String busca,
                                   @Param("categoria") ProdutoCategoria categoria,
                                   @Param("ativo") Boolean ativo,
                                   Pageable pageable);
}
