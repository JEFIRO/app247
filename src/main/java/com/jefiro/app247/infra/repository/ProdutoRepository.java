package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.Produto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ProdutoRepository extends JpaRepository<Produto, String> {
    Optional<Produto> findByCodigoAndEmpresaId(String codigo, String empresaId);
    boolean existsByCodigoAndEmpresaId(String codigo, String empresaId);
    boolean existsByCodigoAndEmpresaIdAndIdProdutoNot(String codigo, String empresaId, String idProduto);
    Optional<Produto> findByIdProdutoAndEmpresaId(String id, String empresaId);
    List<Produto> findAllByUpdateAtAfterAndEmpresaId(LocalDateTime lastSync, String empresaId);
    List<Produto> findTop10ByEmpresaIdOrderByCreateAtDesc(String empresaId);
    org.springframework.data.domain.Page<Produto> findAllByEmpresaId(String empresaId, org.springframework.data.domain.Pageable pageable);

    List<Produto> findAllByUpdateAtAfter(LocalDateTime lastSync);

    List<Produto> findTop10ByOrderByCreateAtDesc();
}
