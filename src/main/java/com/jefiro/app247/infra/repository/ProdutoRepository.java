package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.Produto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProdutoRepository extends JpaRepository<Produto, String> {
    Optional<Produto> findByCodigoInternoAndEmpresaId(String codigo, String empresaId);
    boolean existsByCodigoInternoAndEmpresaId(String codigo, String empresaId);
    boolean existsByCodigoInternoAndEmpresaIdAndIdProdutoNot(String codigo, String empresaId, String idProduto);
    Optional<Produto> findByIdProdutoAndEmpresaId(String id, String empresaId);
    List<Produto> findTop10ByEmpresaIdOrderByCreatedAtDesc(String empresaId);
    org.springframework.data.domain.Page<Produto> findAllByEmpresaId(String empresaId, org.springframework.data.domain.Pageable pageable);
    boolean existsByEmpresaIdAndAtivoTrue(String empresaId);
    List<Produto> findAllByEmpresaIdAndDisponivelTodosCondominiosTrue(String empresaId);

    @org.springframework.data.jpa.repository.Query("""
            select count(p) from Produto p
            where p.empresa.id = :empresaId
              and (p.estoqueMinimoCentral is not null or p.estoqueMinimoCondominio is not null)
            """)
    long countComEstoqueMinimo(@org.springframework.data.repository.query.Param("empresaId") String empresaId);

    /** Produtos ativos abaixo do mínimo no central ou em algum Condomínio onde estão à venda. */
    @org.springframework.data.jpa.repository.Query("""
            select count(p) from Produto p
            where p.empresa.id = :empresaId and p.ativo = true and (
                (p.estoqueMinimoCentral is not null and not exists (
                    select e.id from EstoqueEmpresa e
                    where e.produto = p and e.quantidade >= p.estoqueMinimoCentral))
                or (p.estoqueMinimoCondominio is not null and exists (
                    select c.id from EstoqueCondominio c
                    where c.produto = p and c.ativo = true and c.condominio.ativo = true
                      and c.quantidade < p.estoqueMinimoCondominio)))
            """)
    long countAbaixoDoEstoqueMinimo(@org.springframework.data.repository.query.Param("empresaId") String empresaId);
}
