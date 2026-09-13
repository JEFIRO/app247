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
}
