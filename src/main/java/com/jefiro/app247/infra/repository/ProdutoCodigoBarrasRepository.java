package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.ProdutoCodigoBarras;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProdutoCodigoBarrasRepository extends JpaRepository<ProdutoCodigoBarras,String> {
    Optional<ProdutoCodigoBarras> findByEmpresaIdAndCodigoBarrasAndAtivoTrue(String empresaId,String codigoBarras);
    boolean existsByEmpresaIdAndCodigoBarras(String empresaId,String codigoBarras);
    boolean existsByEmpresaIdAndCodigoBarrasAndProdutoIdProdutoNot(String empresaId,String codigoBarras,String produtoId);
    List<ProdutoCodigoBarras> findAllByProdutoIdProdutoOrderByPrincipalDescCreatedAtAsc(String produtoId);
}
