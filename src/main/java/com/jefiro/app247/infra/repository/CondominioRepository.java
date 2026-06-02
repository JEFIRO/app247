package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.Condominio;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CondominioRepository extends JpaRepository<Condominio, Long> {

}
