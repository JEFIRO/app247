package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.PerfilTributario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PerfilTributarioRepository extends JpaRepository<PerfilTributario, String> {
    Optional<PerfilTributario> findByIdAndEmpresaId(String id, String empresaId);
}
