package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.Planograma;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlanogramaRepository extends JpaRepository<Planograma, String> {
    List<Planograma> findAllByEmpresaIdOrderByAtivoDescNomeAsc(String empresaId);
    Optional<Planograma> findByIdAndEmpresaId(String id, String empresaId);
    boolean existsByEmpresaIdAndNomeIgnoreCaseAndIdNot(String empresaId, String nome, String id);
    boolean existsByEmpresaIdAndNomeIgnoreCase(String empresaId, String nome);
}
