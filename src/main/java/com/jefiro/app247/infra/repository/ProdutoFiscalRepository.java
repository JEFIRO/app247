package com.jefiro.app247.infra.repository;
import com.jefiro.app247.domain.model.ProdutoFiscal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface ProdutoFiscalRepository extends JpaRepository<ProdutoFiscal,String>{
    Optional<ProdutoFiscal> findByProdutoIdProdutoAndEmpresaId(String produtoId,String empresaId);
}
