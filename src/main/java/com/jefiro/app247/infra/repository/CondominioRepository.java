package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.Condominio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CondominioRepository extends JpaRepository<Condominio, String> {
    List<Condominio> findAllByEmpresaIdOrderByNome(String empresaId);

    Optional<Condominio> findByIdCondominioAndEmpresaId(String condominioId, String empresaId);
}
