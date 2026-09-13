package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.PlanogramaPosicao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlanogramaPosicaoRepository extends JpaRepository<PlanogramaPosicao, String> {
    Optional<PlanogramaPosicao> findByIdAndEmpresaId(String id, String empresaId);
    List<PlanogramaPosicao> findAllByPlanogramaIdAndEmpresaIdOrderByOrdemAscIdAsc(String planogramaId, String empresaId);
}
