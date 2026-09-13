package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.PlanogramaProduto;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlanogramaProdutoRepository extends JpaRepository<PlanogramaProduto, String> {
    @EntityGraph(attributePaths={"produto","posicao","posicao.planograma"})
    List<PlanogramaProduto> findAllByPosicaoPlanogramaIdAndEmpresaIdOrderByPosicaoOrdemAscCreatedAtAsc(
            String planogramaId, String empresaId);
    Optional<PlanogramaProduto> findByIdAndEmpresaId(String id, String empresaId);
    Optional<PlanogramaProduto> findByPosicaoIdAndProdutoIdProduto(String posicaoId, String produtoId);
    @EntityGraph(attributePaths={"posicao","posicao.planograma"})
    List<PlanogramaProduto> findAllByEmpresaIdAndProdutoIdProdutoInAndAtivoTrue(
            String empresaId, java.util.Collection<String> produtoIds);
}
