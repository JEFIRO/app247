package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.MovimentacaoEstoque;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MovimentacaoEstoqueRepository extends JpaRepository<MovimentacaoEstoque, String> {
    boolean existsByChaveIdempotencia(String chave);
    List<MovimentacaoEstoque> findAllByEstoqueCondominioIdCondominioAndEstoqueCondominioEmpresaIdOrderByCreatedAtDesc(
            String condominioId, String empresaId);
    List<MovimentacaoEstoque> findAllByEstoqueEmpresaEmpresaIdOrderByCreatedAtDesc(String empresaId);
    List<MovimentacaoEstoque> findAllByTransferenciaIdOrderByCreatedAtAsc(String transferenciaId);
    List<MovimentacaoEstoque> findAllByInventarioIdOrderByCreatedAtAsc(String inventarioId);
}
