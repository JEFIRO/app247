package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.Empresa;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmpresaRepository extends JpaRepository<Empresa,String> {


}
